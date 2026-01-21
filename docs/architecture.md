## 整体设计

没力气用英文写了。

本文档描述简化 RI32V CPU 模拟器第三版（对应rvsim3文件夹部分）。

本 CPU 模拟器具体包含以下元件：

MI (MemoryInterface)，IF (InstructionFetcher)，Dec (Decoder), DU (DispatchUnit), RS (ReservationStation), ALU (ArithmeticLogicUnit), LSQ (LoadStoreQueue), RoB (ReorderBuffer), RF (RegisterFile), Pred (Predictor), CDB (CommonDataBus)，FreeList
以及抽象为接口而非元件的 FlushPipeline，设计上不在CPU内部但仍然模拟为元件的 RAM。

- RAM
与 MI 相连。
单个 SyncReadMem 模拟，提供4MB，配置 .text = 0x0020 0000，.data 由程序分配，一般程序分配栈空间 128KB.
特殊接口：提供读写窗口，供模拟开始前预加载指令。
每次接到一个读写请求，需要花费一个周期进行操作。
写操作直接完成，读操作仍然需要等待 MI 背压握手后传回数据。
状态：sIdle 空闲 - sBusy 繁忙 - sReady 等待反馈数据

- MI
与 IF/LSQ, RAM 相连。连通 FlushPipeline（接收者）。
整体信息路径为：IF/LSQ -> MI -> RAM(Req) -> RAM(Resp) -> MI -> IF/LSQ，相较于请求-反馈的理论1周期路径，有3个周期的延时。
状态：sIdle 空闲 - sBusy 繁忙（RAM 未回复）- sReady 等待反馈数据
对于写请求，在RAM接受请求时即可反馈
状态：isIF 正在处理的元件（IF/LSQ）
Flush 设置：强制转为 sIdle 状态。
在 Flush 期间 RAM 可能返回不被需要的数据。为此，设置一个 epoch 寄存器，在检测到 flush 信号发出的第一个周期（这个需要另一个1-bit寄存器判定）时更新 epoch。发送请求时带有当前 epoch 信息，接收数据时检查 epoch 信息，如果不匹配则丢弃数据，直接进入 sIdle 而非 sReady 状态。
本设计能保证不同批次的 Flush 信号间有间隔，以及 RAM 处理不会经历2次 Flush 还没返回，所以 epoch 设置为 1-bit 即可。

- IF
与 MI, Pred, Dec 相连。连通 FlushPipeline（接收者）。
状态：sIdle 空闲 - sBusy 取指中 - sPred 预测pc中 - sReady 等待提交原始指令
特殊状态： isTerminated 是否曾取指为终止提示指令，该状态为真时无条件将自身置于 sIdle 状态（拒绝进一步取指）。可能被 Flush 重设。
Flush 设置：强制转为 sIdle 状态，根据FlushPipeline的携带信息重设 pc，重置 isTerminated。
sPred 状态只会被跳转指令（br，jmp，...）触发。

- Dec
与 IF，DU 相连。连通 FlushPipeline。
状态：sIdle 空闲 - sBusy 解码中 - sReady 等待提交解码后指令
Flush 设置：强制转为 sIdle 状态。

- DU
维护一个逻辑/物理寄存器关系表 RAT。
与 Pred，RoB，LSQ，RS，FreeList 相连，接通 FlushPipeline（接收者）。
接收 Pred 传来的解析后指令，向 FreeList 给逻辑寄存器分配物理寄存器并更新 RAT，将这个分配的相关信息发给 RoB 获取 robIdx，根据指令种类将其分给 LSQ/RS。
状态：sIdle - sAllocPhys - sWaitPhys - sAllocRoB - sWaitRoB - sDispatch
Flush 设置：强制转为 sIdle 状态，并根据 RoB 本周期发来的 physIdx 与 prePhysIdx 回退 RAT。Flush 状态解除时 RAT 应当回退到正确版本。

- RS
与 DU，ALU 相连，接通 CDB（接收者） 与 FlushPipeline（接收者）。
维护有一系列槽位（乱序列表），每个槽位状态：
rs1: isReady, value, robIdx
rs2: isReady, value, robIdx 可能为imm数据
decInst: 该指令内容
预留槽位满且不存在可推送槽位时，拒绝 DU 输入信号。
Flush 设置：无条件清空所有槽位。

- ALU
与 RS 相连，接通 CDB（发出者） 与 FlushPipeline（接收者）。
根据 decInstr 的内容执行相应内容。应指令类型可能花费不同周期数。
状态：sIdle - sBusy - sReady
繁忙 / 准备好的数据未被 CDB 推送前，拒绝 RS 输入信号。

- LSQ
与 DU，MI，RoB 相连，接通 CDB（发出者，接收者）与 FlushPipeline（接收者）
维护有一系列槽位（循环队列），每个槽位状态：
isWrite, mask(byte, half, word)
addr: isReady, value, robIdx
value: isReady, value, robIdx
rd: targetRobIdx, readyToIssue
读操作不需要 RoB 确认，但是要确认没有同地址的写操作排在前面。
写操作必须在 addr 与 value 准备好，且 RoB 确认后执行。
预留槽位满且不存在可推送槽位时，拒绝 DU 输入信号。
Flush 设置：无条件清空所有槽位。

- RoB
与 DU，LSQ，Pred，RF，FreeList 相连，接通 CDB（接收者） 与 FlushPipeline（发出者）。
维护有一系列槽位（循环队列），每个槽位状态：
rd/realPC: robIdx, decInstType, isReady, value, predPC，physIdx, prePhysIdx
预留槽位满且不存在可推送槽位时，拒绝 DU 槽位分配请求信号。
推送槽位时，如果是寄存器写入指令则向 RF 根据 physIdx 提交写入。
推送槽位时，如果是内存读写指令则向 LSQ 根据 robIdx 提交操作许可。
推送槽位时，如果是跳转指令则向 Pred 提交 realPC，如果与 predPC 不一致则同时发起 Flush 信号。
Flush 设置：按循环队列逆向，根据失败槽位的 physIdx 与 prePhysIdx 逐个向 FreeList 返还 + 向 DU 发送记录重置 RAT，每个周期一个。

- RF
与 DU，RoB 相连。
设计为存储了一系列逻辑寄存器，其与32个物理寄存器间的映射关系表（RAT）存储在 DU 中。 
在 DU 依据逻辑寄存器标号请求数据（最多为 rs1, rs2 双份）时在下周期返回
在 RoB 依据逻辑寄存器标号请求寄存器写时接收。
不需要提供任何反压信号，一定能完成查询与写入请求。

- Pred
与 IF，RoB 相连。
状态：sIdle - sPred
在 RoB 提交跳转指令时会一并收到预测 pc 与预测结果是否正确（0/1），据此更新自己的预测策略。（RoB会给出足够的信息，不需要再连通FlushPipeline）。该行为优先于 sPred 阶段对提交指令的预测。

- CDB
与 ALU，LSQ 相连（接收），与 RS，RoB，LSQ 相连（广播）
状态：sIdle - sBroadcast
内置一个仲裁器以确定该周期接收哪个数据源的数据。
广播是一次性且强制的，数据接收元件不会提供反压信号，默认必定接收并处理。（监听数据也不涉及什么空间分配失败问题）
LSQ 不需要通过 CDB 接收来自 LSQ 的数据，但仍然需要通过 CDB 接收来自 ALU 的数据。
当 Flush 信号在该周期被传播时，CDB 正在广播的数据无效，须被忽略（并没有给CDB连线FlushPipeline，需要接收者判断）。

- FreeList
与 DU，RoB 相连。
为一个存储了可用物理寄存器编号的循环队列。大小设置为至少 32 + NumRoBSlots + 1 时不会出现分配失败。（+1 是因为 DU 向 FreeList 的分配请求早于向 RoB 的分配请求）
DU 向其申请（还是留一个是否成功的信号），RoB 向其返还。

- FlushPipeline
从 RoB 引出，引至 IF，Dec，DU，RS，MI。
包含失败的跳转指令对应的 robIdx（DU 需要），以及正确的 pc（IF 需要）。
RoB 归还物理寄存器编号给 FreeList 设计为需要多个周期，在此之前 Flush 信号不会结束。
所有具备状态机的 FlushPipeline 接收者都会有“在 Flush 期间将状态设为默认”，这会在 Flush 的每一个周期都执行一次，保证对应元件的阻塞静默。