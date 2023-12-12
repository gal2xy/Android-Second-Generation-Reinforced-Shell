#include <jni.h>
#include <string>
#include <dlfcn.h>
#include <android/log.h>
#include <cstdint>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Tag", __VA_ARGS__)

/* 以下是 OpenMemory函数在内存中对外的方法名 */
/*Android 5*/
#define OpenMemory21 "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPS9_"
/*Android 5.1*/
#define OpenMemory22 "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_7OatFileEPS9_"
/*Android 6*/
#define OpenMemory23 "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_10OatDexFileEPS9_"
/*Android 7.1, 与Android 6一致*/
//#define openMemory25 "_ZN3art7DexFile10OpenMemoryEPKhjRKNSt3__112basic_stringIcNS3_11char_traitsIcEENS3_9allocatorIcEEEEjPNS_6MemMapEPKNS_10OatDexFileEPS9_"

/*定义函数指针*/
typedef void *(*org_artDexFileOpenMemory21)(const uint8_t *base,
                                            size_t size,
                                            const std::string &location,
                                            uint32_t location_checksum,
                                            void *mem_map,
                                            std::string *error_msg);

typedef void *(*org_artDexFileOpenMemory22)(const uint8_t *base,
                                            size_t size,
                                            const std::string &location,
                                            uint32_t location_checksum,
                                            void *mem_map,
                                            const void *oat_file,
                                            std::string *error_msg);

typedef std::unique_ptr<const void *> (*org_artDexFileOpenMemory23)(const uint8_t *base,
                                                                    size_t size,
                                                                    const std::string &location,
                                                                    uint32_t location_checksum,
                                                                    void *mem_map,
                                                                    const void *oat_dex_file,
                                                                    std::string *error_msg);

static const size_t kSha1DigestSize = 20;
//放头文件中一堆错误！?
// Raw header_item.
struct Header
{
    uint8_t  magic_[8];
    uint32_t checksum_;        // See also location_checksum_
    uint8_t  signature_[kSha1DigestSize];
    uint32_t file_size_;       // size of entire file
    uint32_t header_size_;     // offset to start of next section
    uint32_t endian_tag_;
    uint32_t link_size_;       // unused
    uint32_t link_off_;        // unused
    uint32_t map_off_;         // unused
    uint32_t string_ids_size_; // number of StringIds
    uint32_t string_ids_off_;  // file offset of StringIds array
    uint32_t type_ids_size_;   // number of TypeIds, we don't support more than 65535
    uint32_t type_ids_off_;    // file offset of TypeIds array
    uint32_t proto_ids_size_;  // number of ProtoIds, we don't support more than 65535
    uint32_t proto_ids_off_;   // file offset of ProtoIds array
    uint32_t field_ids_size_;  // number of FieldIds
    uint32_t field_ids_off_;   // file offset of FieldIds array
    uint32_t method_ids_size_; // number of MethodIds
    uint32_t method_ids_off_;  // file offset of MethodIds array
    uint32_t class_defs_size_; // number of ClassDefs
    uint32_t class_defs_off_;  // file offset of ClassDef array
    uint32_t data_size_;       // unused
    uint32_t data_off_;        // unused
};

//libart.so指针
void* artHandle = nullptr;


void* loadDexInAndroid5(int sdk_int, const char *base, size_t size);
std::unique_ptr<const void *> loadDexAboveAndroid6(const char *base, size_t size);

/*加载内存dex*/
extern "C"
JNIEXPORT jobject * JNICALL
Java_com_example_androidsecondshell2_MyClassLoader_OpenMemory(JNIEnv *env, jclass clazz,
                                                              jbyteArray dex, jlong dexlen, jint sdk_int) {
    // TODO: implement OpenMemory()
    void* value;

    if (sdk_int < 22) {/* android 5.0, 5.1*/
        value =  loadDexInAndroid5(sdk_int,(char*)dex,(size_t)dexlen);
    } else{/* android 6.0 7.0 7.1 */
        value = loadDexAboveAndroid6((char*)dex,(size_t)dexlen).get();
    }

    if (!value){
        LOGE("fail to load dex");
    }

    return (jobject*) value;

}
/* 加载内存dex，适用于android 5, 5.1 */
void* loadDexInAndroid5(int sdk_int, const char *base, size_t size){
    std::string location = "Anonymous-DexFile";
    std::string err_msg;
    void* value;

    const auto *dex_header = reinterpret_cast<const Header *>(base);

    if (sdk_int == 21) {/* android 5.0 */
        auto func21 = (org_artDexFileOpenMemory21) dlsym(artHandle, OpenMemory21);
        value = func21((const unsigned char *) base,
                       (size_t)size,
                       location,
                       dex_header->checksum_,
                       nullptr,
                       &err_msg);
    } else if (sdk_int == 22) {/* android 5.1 */
        auto func22 = (org_artDexFileOpenMemory22) dlsym(artHandle, OpenMemory22);
        value = func22((const unsigned char *) base,
                       size,
                       location,
                       dex_header->checksum_,
                       nullptr,
                       nullptr,
                       &err_msg);
    }

    if (!value){
        LOGE("fail to load dex in Android 5");
    }

    return value;

}

/* 加载内存dex，适用于android 6.0 7.0 7.1 */
std::unique_ptr<const void *> loadDexAboveAndroid6(const char *base, size_t size){

    std::string location = "Anonymous-DexFile";
    std::string err_msg;
    std::unique_ptr<const void *> value;

    const auto *dex_header = reinterpret_cast<const Header *>(base);

    auto func23 = (org_artDexFileOpenMemory23) dlsym(artHandle, OpenMemory23);
    value = func23((const unsigned char *) base,
                   size,
                   location,
                   dex_header->checksum_,
                   nullptr,
                   nullptr,
                   &err_msg);

    if (!value) {
        LOGE("fail to load dex in Android 6 and above");
        return nullptr;
    }

    return value;

}


JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved){
    //打开libart.so文件
    artHandle = (void*)dlopen("libart.so", RTLD_LAZY);

}


