pub mod file_service;
pub mod data_service;
pub mod vector_service;
pub mod cache;
pub mod error;
pub mod models;
pub mod config;
pub mod crypto;

pub use error::{CloudpoolError, Result};
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::{jbyteArray, jstring};
use file_service::FileService;

fn null_check_byte_array(env: &mut JNIEnv<'_>, data: jbyteArray) -> bool {
    if data.is_null() {
        let _ = env.throw_new("java/lang/IllegalArgumentException", "Data array is null");
        let _ = env.exception_check();
        return true;
    }
    false
}

fn null_check_string(env: &mut JNIEnv<'_>, s: jstring) -> bool {
    if s.is_null() {
        let _ = env.throw_new("java/lang/IllegalArgumentException", "String argument is null");
        let _ = env.exception_check();
        return true;
    }
    false
}

fn throw_sanitized(env: &mut JNIEnv<'_>, class: &str, msg: &str) {
    let _ = env.throw_new(class, msg);
    let _ = env.exception_check();
}

#[no_mangle]
pub extern "system" fn Java_com_cloudpool_util_RustBridge_calculateChecksum<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    data: jbyteArray,
) -> jstring {
    if null_check_byte_array(&mut env, data) {
        return std::ptr::null_mut();
    }
    let jarray = unsafe { jni::objects::JByteArray::from_raw(data) };
    let bytes = match env.convert_byte_array(&jarray) {
        Ok(b) => b,
        Err(_) => {
            throw_sanitized(&mut env, "java/lang/IllegalArgumentException", "Failed to convert byte array");
            return std::ptr::null_mut();
        }
    };

    match FileService::calculate_checksum(&bytes) {
        Ok(checksum) => match env.new_string(&checksum) {
            Ok(jstr) => jstr.into_raw(),
            Err(_) => {
                throw_sanitized(&mut env, "java/lang/RuntimeException", "Failed to create Java string");
                std::ptr::null_mut()
            }
        },
        Err(_) => {
            throw_sanitized(&mut env, "java/lang/RuntimeException", "Checksum calculation failed");
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
    if null_check_byte_array(&mut env, data) {
        return std::ptr::null_mut();
    }
    let jarray = unsafe { jni::objects::JByteArray::from_raw(data) };
    let bytes = match env.convert_byte_array(&jarray) {
        Ok(b) => b,
        Err(_) => {
            throw_sanitized(&mut env, "java/lang/IllegalArgumentException", "Failed to convert byte array");
            return std::ptr::null_mut();
        }
    };

    match FileService::compress(&bytes) {
        Ok(compressed) => match env.byte_array_from_slice(&compressed) {
            Ok(jarr) => jarr.into_raw(),
            Err(_) => {
                throw_sanitized(&mut env, "java/lang/RuntimeException", "Failed to create Java byte array");
                std::ptr::null_mut()
            }
        },
        Err(_) => {
            throw_sanitized(&mut env, "java/lang/RuntimeException", "Compression failed");
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
    if null_check_byte_array(&mut env, data) {
        return std::ptr::null_mut();
    }
    let jarray = unsafe { jni::objects::JByteArray::from_raw(data) };
    let bytes = match env.convert_byte_array(&jarray) {
        Ok(b) => b,
        Err(_) => {
            throw_sanitized(&mut env, "java/lang/IllegalArgumentException", "Failed to convert byte array");
            return std::ptr::null_mut();
        }
    };

    match FileService::decompress(&bytes) {
        Ok(decompressed) => match env.byte_array_from_slice(&decompressed) {
            Ok(jarr) => jarr.into_raw(),
            Err(_) => {
                throw_sanitized(&mut env, "java/lang/RuntimeException", "Failed to create Java byte array");
                std::ptr::null_mut()
            }
        },
        Err(_) => {
            throw_sanitized(&mut env, "java/lang/RuntimeException", "Decompression failed");
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
    if null_check_string(&mut env, file_path) {
        return std::ptr::null_mut();
    }
    let jstr = unsafe { jni::objects::JString::from_raw(file_path) };
    let path_str: String = match env.get_string(&jstr) {
        Ok(s) => s.into(),
        Err(_) => {
            throw_sanitized(&mut env, "java/lang/IllegalArgumentException", "Failed to get CSV file path");
            return std::ptr::null_mut();
        }
    };

    match crate::data_service::DataService::parse_csv(&path_str) {
        Ok(json_data) => match env.new_string(&json_data) {
            Ok(jstr) => jstr.into_raw(),
            Err(_) => {
                throw_sanitized(&mut env, "java/lang/RuntimeException", "Failed to create Java string");
                std::ptr::null_mut()
            }
        },
        Err(_) => {
            throw_sanitized(&mut env, "java/lang/RuntimeException", "CSV parsing failed");
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
    if null_check_byte_array(&mut env, data) {
        return std::ptr::null_mut();
    }
    let jarray = unsafe { jni::objects::JByteArray::from_raw(data) };
    let bytes = match env.convert_byte_array(&jarray) {
        Ok(b) => b,
        Err(_) => {
            throw_sanitized(&mut env, "java/lang/IllegalArgumentException", "Failed to convert byte array");
            return std::ptr::null_mut();
        }
    };

    match FileService::convert_to_webp(&bytes) {
        Ok(webp_bytes) => match env.byte_array_from_slice(&webp_bytes) {
            Ok(jarr) => jarr.into_raw(),
            Err(_) => {
                throw_sanitized(&mut env, "java/lang/RuntimeException", "Failed to create Java byte array");
                std::ptr::null_mut()
            }
        },
        Err(_) => {
            throw_sanitized(&mut env, "java/lang/RuntimeException", "Image conversion failed");
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_cloudpool_util_RustBridge_cosineSimilarity<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    vec1: jni::sys::jfloatArray,
    vec2: jni::sys::jfloatArray,
) -> jni::sys::jfloat {
    if vec1.is_null() || vec2.is_null() {
        throw_sanitized(&mut env, "java/lang/IllegalArgumentException", "Vector array is null");
        return -2.0;
    }
    
    let jarray1 = unsafe { jni::objects::JFloatArray::from_raw(vec1) };
    let jarray2 = unsafe { jni::objects::JFloatArray::from_raw(vec2) };
    
    let vec1_len = env.get_array_length(&jarray1).unwrap_or(0);
    let vec2_len = env.get_array_length(&jarray2).unwrap_or(0);
    
    if vec1_len != vec2_len {
        throw_sanitized(&mut env, "java/lang/IllegalArgumentException", "Vectors must have the same dimension");
        return -2.0;
    }

    let mut v1 = vec![0.0; vec1_len as usize];
    let mut v2 = vec![0.0; vec2_len as usize];
    
    if env.get_float_array_region(&jarray1, 0, &mut v1).is_err() {
        throw_sanitized(&mut env, "java/lang/RuntimeException", "Failed to get vector 1 elements");
        return -2.0;
    }
    if env.get_float_array_region(&jarray2, 0, &mut v2).is_err() {
        throw_sanitized(&mut env, "java/lang/RuntimeException", "Failed to get vector 2 elements");
        return -2.0;
    }

    match crate::vector_service::VectorService::cosine_similarity(&v1, &v2) {
        Ok(sim) => sim,
        Err(_) => {
            throw_sanitized(&mut env, "java/lang/RuntimeException", "Cosine similarity calculation failed");
            -2.0
        }
    }
}
