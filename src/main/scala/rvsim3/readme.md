所有文件放在同一个文件夹下，不分子文件夹。

CDB/MI 的双边 bundle 都存放在对应文件中，FlushPipeline，DecodedInst/InstType，循环队列，二分优先编码器等各自独立文件存储。

其他内部 bundle 统一为在对应的发送方文件中定义，名称为 SrcToDest 格式，比如 RAM 到 MI 的 bundle 命名为 RAMToMI，定义在 RAM 的对应文件中。

RAT 为 DU 的附属结构，定义在 DU 的对应文件中。

DecodedInst 最后带上了该指令对应的来源 pc。