use crate::{CloudpoolError, Result};

/// Vector service for handling vector operations
pub struct VectorService;

impl VectorService {
    /// Calculate cosine similarity between two vectors
    pub fn cosine_similarity(vec1: &[f32], vec2: &[f32]) -> Result<f32> {
        if vec1.len() != vec2.len() {
            return Err(CloudpoolError::InvalidInput(
                "Vectors must have the same dimension".to_string(),
            ));
        }

        let (dot_product, magnitude1_sq, magnitude2_sq) = cosine_components(vec1, vec2);
        let magnitude1 = magnitude1_sq.sqrt();
        let magnitude2 = magnitude2_sq.sqrt();

        if magnitude1 == 0.0 || magnitude2 == 0.0 {
            return Ok(0.0);
        }

        Ok(dot_product / (magnitude1 * magnitude2))
    }

    /// Calculate Euclidean distance between two vectors
    pub fn euclidean_distance(vec1: &[f32], vec2: &[f32]) -> Result<f32> {
        if vec1.len() != vec2.len() {
            return Err(CloudpoolError::InvalidInput(
                "Vectors must have the same dimension".to_string(),
            ));
        }

        let sum: f32 = vec1
            .iter()
            .zip(vec2.iter())
            .map(|(a, b)| (a - b).powi(2))
            .sum();

        Ok(sum.sqrt())
    }

    /// Normalize vector
    pub fn normalize(vector: &[f32]) -> Result<Vec<f32>> {
        let magnitude = (vector.iter().map(|x| x * x).sum::<f32>()).sqrt();

        if magnitude == 0.0 {
            return Err(CloudpoolError::InvalidInput(
                "Cannot normalize zero vector".to_string(),
            ));
        }

        Ok(vector.iter().map(|x| x / magnitude).collect())
    }

    /// Find k nearest neighbors
    pub fn knn(query_vector: &[f32], vectors: &[Vec<f32>], k: usize) -> Result<Vec<(usize, f32)>> {
        let mut distances: Vec<(usize, f32)> = Vec::with_capacity(vectors.len());
        for (idx, vec) in vectors.iter().enumerate() {
            let dist = Self::cosine_similarity(query_vector, vec)?;
            distances.push((idx, dist));
        }

        distances.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));

        Ok(distances.into_iter().take(k).collect())
    }
}

fn cosine_components(vec1: &[f32], vec2: &[f32]) -> (f32, f32, f32) {
    #[cfg(any(target_arch = "x86", target_arch = "x86_64"))]
    {
        if std::is_x86_feature_detected!("sse") {
            return unsafe { cosine_components_sse(vec1, vec2) };
        }
    }

    #[cfg(target_arch = "aarch64")]
    {
        unsafe { cosine_components_neon(vec1, vec2) }
    }

    #[cfg(not(target_arch = "aarch64"))]
    {
        cosine_components_scalar(vec1, vec2)
    }
}

#[cfg(not(target_arch = "aarch64"))]
fn cosine_components_scalar(vec1: &[f32], vec2: &[f32]) -> (f32, f32, f32) {
    let mut dot = 0.0;
    let mut magnitude1_sq = 0.0;
    let mut magnitude2_sq = 0.0;

    for (a, b) in vec1.iter().zip(vec2.iter()) {
        dot += a * b;
        magnitude1_sq += a * a;
        magnitude2_sq += b * b;
    }

    (dot, magnitude1_sq, magnitude2_sq)
}

#[cfg(target_arch = "x86")]
use std::arch::x86 as x86_arch;

#[cfg(target_arch = "x86_64")]
use std::arch::x86_64 as x86_arch;

#[cfg(any(target_arch = "x86", target_arch = "x86_64"))]
#[target_feature(enable = "sse")]
unsafe fn cosine_components_sse(vec1: &[f32], vec2: &[f32]) -> (f32, f32, f32) {
    let mut dot_vec = x86_arch::_mm_setzero_ps();
    let mut magnitude1_vec = x86_arch::_mm_setzero_ps();
    let mut magnitude2_vec = x86_arch::_mm_setzero_ps();
    let chunks_len = vec1.len() / 4 * 4;

    for idx in (0..chunks_len).step_by(4) {
        let a = x86_arch::_mm_loadu_ps(vec1.as_ptr().add(idx));
        let b = x86_arch::_mm_loadu_ps(vec2.as_ptr().add(idx));

        dot_vec = x86_arch::_mm_add_ps(dot_vec, x86_arch::_mm_mul_ps(a, b));
        magnitude1_vec = x86_arch::_mm_add_ps(magnitude1_vec, x86_arch::_mm_mul_ps(a, a));
        magnitude2_vec = x86_arch::_mm_add_ps(magnitude2_vec, x86_arch::_mm_mul_ps(b, b));
    }

    let mut dot_parts = [0.0; 4];
    let mut magnitude1_parts = [0.0; 4];
    let mut magnitude2_parts = [0.0; 4];
    x86_arch::_mm_storeu_ps(dot_parts.as_mut_ptr(), dot_vec);
    x86_arch::_mm_storeu_ps(magnitude1_parts.as_mut_ptr(), magnitude1_vec);
    x86_arch::_mm_storeu_ps(magnitude2_parts.as_mut_ptr(), magnitude2_vec);

    let mut dot = dot_parts.iter().sum();
    let mut magnitude1_sq = magnitude1_parts.iter().sum();
    let mut magnitude2_sq = magnitude2_parts.iter().sum();

    for idx in chunks_len..vec1.len() {
        let a = vec1[idx];
        let b = vec2[idx];
        dot += a * b;
        magnitude1_sq += a * a;
        magnitude2_sq += b * b;
    }

    (dot, magnitude1_sq, magnitude2_sq)
}

#[cfg(target_arch = "aarch64")]
unsafe fn cosine_components_neon(vec1: &[f32], vec2: &[f32]) -> (f32, f32, f32) {
    use std::arch::aarch64;

    let mut dot_vec = aarch64::vdupq_n_f32(0.0);
    let mut magnitude1_vec = aarch64::vdupq_n_f32(0.0);
    let mut magnitude2_vec = aarch64::vdupq_n_f32(0.0);
    let chunks_len = vec1.len() / 4 * 4;

    for idx in (0..chunks_len).step_by(4) {
        let a = aarch64::vld1q_f32(vec1.as_ptr().add(idx));
        let b = aarch64::vld1q_f32(vec2.as_ptr().add(idx));

        dot_vec = aarch64::vaddq_f32(dot_vec, aarch64::vmulq_f32(a, b));
        magnitude1_vec = aarch64::vaddq_f32(magnitude1_vec, aarch64::vmulq_f32(a, a));
        magnitude2_vec = aarch64::vaddq_f32(magnitude2_vec, aarch64::vmulq_f32(b, b));
    }

    let mut dot = aarch64::vaddvq_f32(dot_vec);
    let mut magnitude1_sq = aarch64::vaddvq_f32(magnitude1_vec);
    let mut magnitude2_sq = aarch64::vaddvq_f32(magnitude2_vec);

    for idx in chunks_len..vec1.len() {
        let a = vec1[idx];
        let b = vec2[idx];
        dot += a * b;
        magnitude1_sq += a * a;
        magnitude2_sq += b * b;
    }

    (dot, magnitude1_sq, magnitude2_sq)
}

#[cfg(test)]
mod tests {
    use super::VectorService;

    fn assert_close(actual: f32, expected: f32) {
        assert!(
            (actual - expected).abs() < 1e-6,
            "expected {expected}, got {actual}"
        );
    }

    #[test]
    fn cosine_similarity_returns_one_for_same_direction() {
        let vec1 = [1.0, 2.0, 3.0, 4.0, 5.0];
        let vec2 = [2.0, 4.0, 6.0, 8.0, 10.0];

        assert_close(VectorService::cosine_similarity(&vec1, &vec2).unwrap(), 1.0);
    }

    #[test]
    fn cosine_similarity_returns_zero_for_orthogonal_vectors() {
        let vec1 = [1.0, 0.0, 0.0];
        let vec2 = [0.0, 1.0, 0.0];

        assert_close(VectorService::cosine_similarity(&vec1, &vec2).unwrap(), 0.0);
    }

    #[test]
    fn cosine_similarity_returns_zero_for_zero_vector() {
        let vec1 = [0.0, 0.0, 0.0];
        let vec2 = [1.0, 2.0, 3.0];

        assert_close(VectorService::cosine_similarity(&vec1, &vec2).unwrap(), 0.0);
    }

    #[test]
    fn cosine_similarity_rejects_mismatched_dimensions() {
        let err = VectorService::cosine_similarity(&[1.0, 2.0], &[1.0]).unwrap_err();

        assert!(err.to_string().contains("same dimension"));
    }
}
