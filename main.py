from zlib import adler32
from hashlib import sha1
from binascii import unhexlify
import zipfile

def fixCheckSum(dexBytesArray):
    # dexfile[8:12]
    # 小端存储
    value = adler32(bytes(dexBytesArray[12:]))
    valueArray = bytearray(value.to_bytes(4, 'little'))
    for i in range(len(valueArray)):
        dexBytesArray[8 + i] = valueArray[i]


def fixSignature(dexBytesArray):
    # dexfile[12:32]
    sha_1 = sha1()
    sha_1.update(bytes(dexBytesArray[32:]))
    value = sha_1.hexdigest()
    valueArray = bytearray(unhexlify(value))
    for i in range(len(valueArray)):
        dexBytesArray[12 + i] = valueArray[i]


def fixFileSize(dexBytesArray, fileSize):
    # dexfile[32:36]
    # 小端存储
    fileSizeArray = bytearray(fileSize.to_bytes(4, "little"))
    for i in range(len(fileSizeArray)):
        dexBytesArray[32 + i] = fileSizeArray[i]


def encrypto(file):
    for i in range(len(file)):
        file[i] ^= 0xff
    
    return file


def readDexsSourceApk(sourceApkPath):
    dexList = []
    with zipfile.ZipFile(sourceApkPath, "r") as sourceApk:
        # 获取apk中所有的文件路径名称
        nameList = sourceApk.namelist()
        for fileName in nameList:
            # 提取dex文件
            if fileName.endswith(".dex"):
                # 解压缩到当前目录下 sourceApk.extract(fileName)
                # 建议直接读取
                with sourceApk.open(fileName) as dexfile:
                    data = bytearray(dexfile.read())
                    dexList.append(data)

    return dexList


def readDexFromShellApk(shellApkPath):
    with zipfile.ZipFile(shellApkPath, "r") as sourceApk:
        # 获取apk中所有的文件路径名称
        nameList = sourceApk.namelist()
        for fileName in nameList:
            # 提取dex文件
            if fileName.endswith(".dex"):
                # 建议直接读取
                with sourceApk.open(fileName) as dexfile:
                    data = bytearray(dexfile.read())
                break

    return data


def combineSourceDexs(sourceDexList):
    combinedDex = bytearray()
    for dexfile in sourceDexList:
        dexfileLen = len(dexfile)
        print(hex(dexfileLen))
        # 首先存放当前dex的大小，四字节，小端存储
        combinedDex += bytearray(dexfileLen.to_bytes(4, 'little'))
        # 然后存放当前dex
        combinedDex += dexfile

    return combinedDex


def forkShellLibIntoSourceApk():
    pass


def start():
    sourceApkPath = "sourceApk.apk"
    shellApkPath = "shellApk.apk"
    newApkPath = "newApk.apk"
    # 读取源APK中的dex文件,可以接受多dex
    sourceDexList = readDexsSourceApk(sourceApkPath)
    # 读取壳程序dex,只能有一个dex
    shellDex = readDexFromShellApk(shellApkPath)

    # 合并源程序dex
    combineSourceDex = combineSourceDexs(sourceDexList)

    # 加密源程序dex
    encSourceDex = encrypto(combineSourceDex)
    encSourceDexLen = len(encSourceDex)

    # 新的dex文件内容 = 壳dex + 加密的源dex + 四字节标识加密后源dex大小长度
    newDex = shellDex + encSourceDex + bytearray(encSourceDexLen.to_bytes(4, 'little'))
    newDexLen = len(newDex)

    # 首先修改filesize
    fixFileSize(newDex, newDexLen)
    # 其次修改signature
    fixSignature(newDex)
    # 最后修改checksum
    fixCheckSum(newDex)

    # 本想用代码实现替换apk中的dex（先删除再写入），但zipfile库没有现成的方法，还是算了
    # 导出成新的dex文件

    # 其实还有一个问题，就是需要把脱壳APK中的lib库复制过来！
    # 待实现（解压缩，替换，再压缩？？？）
    # forkShellLibIntoSourceApk()
    with open(r'classes.dex', 'wb') as f:
        f.write(bytes(newDex))

if __name__ == '__main__':
    start()