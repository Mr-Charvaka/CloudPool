pub mod file_service;
pub mod data_service;
pub mod vector_service;
pub mod cache;
pub mod error;
pub mod models;
pub mod crypto;
pub mod config;

pub use error::{CloudpoolError, Result};
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::{jbyteArray, jstring};
use file_service::FileService;

#[no_mangle]
pub extern "system" fn Java_com_cloudpool_util_RustBridge_calculateChecksum<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    data: jbyteArray,
) -> jstring {
    let jarray = unsafe { jni::objects::JByteArray::from_raw(data) };
    let bytes = match env.convert_byte_array(&jarray) {
        Ok(b) => b,
        Err(_) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", "Failed to convert byte array");
            return std::ptr::null_mut();
        }
    };

    match FileService::calculate_checksum(&bytes) {
        Ok(checksum) => match env.new_string(checksum) {
            Ok(jstr) => jstr.into_raw(),
            Err(_) => {
                let _ = env.throw_new("java/lang/RuntimeException", "Failed to create Java string");
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Checksum calculation failed: {}", e));
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_cloudpool_util_RustBridge_compress<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    data: jbyteArray,
) -> jbyteArray {
    let jarray = unsafe { jni::objects::JByteArray::from_raw(data) };
    let bytes = match env.convert_byte_array(&jarray) {
        Ok(b) => b,
        Err(_) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", "Failed to convert byte array");
            return std::ptr::null_mut();
        }
    };

    match FileService::compress(&bytes) {
        Ok(compressed) => match env.byte_array_from_slice(&compressed) {
            Ok(jarr) => jarr.into_raw(),
            Err(_) => {
                let _ = env.throw_new("java/lang/RuntimeException", "Failed to create Java byte array");
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Compression failed: {}", e));
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_cloudpool_util_RustBridge_decompress<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    data: jbyteArray,
) -> jbyteArray {
    let jarray = unsafe { jni::objects::JByteArray::from_raw(data) };
    let bytes = match env.convert_byte_array(&jarray) {
        Ok(b) => b,
        Err(_) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", "Failed to convert byte array");
            return std::ptr::null_mut();
        }
    };

    match FileService::decompress(&bytes) {
        Ok(decompressed) => match env.byte_array_from_slice(&decompressed) {
            Ok(jarr) => jarr.into_raw(),
            Err(_) => {
                let _ = env.throw_new("java/lang/RuntimeException", "Failed to create Java byte array");
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Decompression failed: {}", e));
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_cloudpool_util_RustBridge_parseCsv<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    file_path: jstring,
) -> jstring {
    let jstr = unsafe { jni::objects::JString::from_raw(file_path) };
    let path_str: String = match env.get_string(&jstr) {
        Ok(s) => s.into(),
        Err(_) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", "Failed to get CSV file path");
            return std::ptr::null_mut();
        }
    };

    match crate::data_service::DataService::parse_csv(&path_str) {
        Ok(json_data) => match env.new_string(json_data) {
            Ok(jstr) => jstr.into_raw(),
            Err(_) => {
                let _ = env.throw_new("java/lang/RuntimeException", "Failed to create Java string");
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("CSV parsing failed: {}", e));
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_cloudpool_util_RustBridge_convertToWebp<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    data: jbyteArray,
) -> jbyteArray {
    let jarray = unsafe { jni::objects::JByteArray::from_raw(data) };
    let bytes = match env.convert_byte_array(&jarray) {
        Ok(b) => b,
        Err(_) => {
            let _ = env.throw_new("java/lang/IllegalArgumentException", "Failed to convert byte array");
            return std::ptr::null_mut();
        }
    };

    match FileService::convert_to_webp(&bytes) {
        Ok(webp_bytes) => match env.byte_array_from_slice(&webp_bytes) {
            Ok(jarr) => jarr.into_raw(),
            Err(_) => {
                let _ = env.throw_new("java/lang/RuntimeException", "Failed to create Java byte array");
                std::ptr::null_mut()
            }
        },
        Err(e) => {
            let _ = env.throw_new("java/lang/RuntimeException", format!("Image conversion failed: {}", e));
            std::ptr::null_mut()
        }
    }
}