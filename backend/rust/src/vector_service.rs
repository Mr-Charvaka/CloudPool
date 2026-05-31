use crate::{CloudpoolError, Result};

/// Vector service for handling vector operations
pub struct VectorService;

impl VectorService {
    /// Calculate cosine similarity between two vectors
    pub fn cosine_similarity(vec1: &[f32], vec2: &[f32]) -> Result<f32> {
        use std::simd::f32x8;
        use std::simd::num::SimdFloat;

        if vec1.len() != vec2.len() {
            return Err(CloudpoolError::InvalidInput(
                "Vectors must have the same dimension".to_string()
            ));
        }

        let mut dot_product = f32x8::splat(0.0);
        let mut sum_sq1 = f32x8::splat(0.0);
        let mut sum_sq2 = f32x8::splat(0.0);

        let (chunks1, rem1) = vec1.as_chunks::<8>();
        let (chunks2, rem2) = vec2.as_chunks::<8>();

        for (c1, c2) in chunks1.iter().zip(chunks2.iter()) {
            let v1 = f32x8::from_array(*c1);
            let v2 = f32x8::from_array(*c2);
            dot_product += v1 * v2;
            sum_sq1 += v1 * v1;
            sum_sq2 += v2 * v2;
        }

        let mut dot = dot_product.reduce_sum();
        let mut m1 = sum_sq1.reduce_sum();
        let mut m2 = sum_sq2.reduce_sum();

        for (a, b) in rem1.iter().zip(rem2.iter()) {
            dot += a * b;
            m1 += a * a;
            m2 += b * b;
        }

        let magnitude1 = m1.sqrt();
        let magnitude2 = m2.sqrt();

        if magnitude1 == 0.0 || magnitude2 == 0.0 {
            return Ok(0.0);
        }

        Ok(dot / (magnitude1 * magnitude2))
    }

    /// Calculate Euclidean distance between two vectors
    pub fn euclidean_distance(vec1: &[f32], vec2: &[f32]) -> Result<f32> {
        if vec1.len() != vec2.len() {
            return Err(CloudpoolError::InvalidInput(
                "Vectors must have the same dimension".to_string()
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
                "Cannot normalize zero vector".to_string()
            ));
        }

        Ok(vector.iter().map(|x| x / magnitude).collect())
    }

    /// Find k nearest neighbors
    pub fn knn(
        query_vector: &[f32],
        vectors: &[Vec<f32>],
        k: usize,
    ) -> Result<Vec<(usize, f32)>> {
        let mut distances: Vec<(usize, f32)> = vectors
            .iter()
            .enumerate()
            .map(|(idx, vec)| {
                let dist = Self::cosine_similarity(query_vector, vec)
                    .unwrap_or(0.0);
                (idx, dist)
            })
            .collect();

        distances.sort_by(|a, b| {
            b.1.partial_cmp(&a.1)
                .unwrap_or(std::cmp::Ordering::Equal)
        });

        Ok(distances.into_iter().take(k).collect())
    }
}
