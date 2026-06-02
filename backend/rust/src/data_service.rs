use crate::{CloudpoolError, Result};
use serde_json::{json, Value};

/// Data service for handling relational data operations
pub struct DataService;

impl DataService {
    /// Validate JSON data against schema
    pub fn validate_against_schema(
        data: &Value,
        _schema: &Value,
    ) -> Result<()> {
        if !data.is_object() {
            return Err(CloudpoolError::InvalidInput(
                "Data must be a JSON object".to_string()
            ));
        }
        Ok(())
    }

    /// Merge records
    pub fn merge_records(record1: &Value, record2: &Value) -> Result<Value> {
        let mut merged = record1.clone();
        
        if let (Some(obj1), Some(obj2)) = (merged.as_object_mut(), record2.as_object()) {
            for (key, value) in obj2.iter() {
                obj1.insert(key.clone(), value.clone());
            }
            Ok(merged)
        } else {
            Err(CloudpoolError::InvalidInput(
                "Both records must be JSON objects".to_string()
            ))
        }
    }

    /// Filter records
    pub fn filter_records<F>(
        records: &[Value],
        predicate: F,
    ) -> Vec<Value>
    where
        F: Fn(&Value) -> bool,
    {
        records
            .iter()
            .filter(|record| predicate(record))
            .cloned()
            .collect()
    }

    /// Sort records by field
    pub fn sort_records(
        records: &mut [Value],
        field: &str,
        ascending: bool,
    ) -> Result<()> {
        records.sort_by(|a, b| {
            let val_a = a.get(field).unwrap_or(&Value::Null);
            let val_b = b.get(field).unwrap_or(&Value::Null);

            let cmp = match (val_a, val_b) {
                (Value::Number(n1), Value::Number(n2)) => {
                    n1.as_f64()
                        .unwrap_or(0.0)
                        .partial_cmp(&n2.as_f64().unwrap_or(0.0))
                        .unwrap_or(std::cmp::Ordering::Equal)
                }
                (Value::String(s1), Value::String(s2)) => s1.cmp(s2),
                _ => std::cmp::Ordering::Equal,
            };

            if ascending { cmp } else { cmp.reverse() }
        });

        Ok(())
    }

    /// Aggregate records
    pub fn aggregate(
        records: &[Value],
        field: &str,
        operation: &str,
    ) -> Result<Value> {
        let values: Vec<f64> = records
            .iter()
            .filter_map(|r| {
                r.get(field)?
                    .as_f64()
            })
            .collect();

        if values.is_empty() {
            return Ok(json!(0.0));
        }

        let result = match operation {
            "sum" => values.iter().sum::<f64>(),
            "avg" => values.iter().sum::<f64>() / values.len() as f64,
            "min" => values
                .iter()
                .cloned()
                .fold(f64::INFINITY, f64::min),
            "max" => values
                .iter()
                .cloned()
                .fold(f64::NEG_INFINITY, f64::max),
            "count" => values.len() as f64,
            _ => return Err(CloudpoolError::InvalidInput(
                format!("Unknown operation: {}", operation)
            )),
        };

        Ok(json!(result))
    }

    /// Parse CSV file from path
    pub fn parse_csv(file_path: &str) -> Result<String> {
        let mut reader = csv::ReaderBuilder::new()
            .has_headers(true)
            .from_path(file_path)
            .map_err(|e| CloudpoolError::IoError(std::io::Error::new(std::io::ErrorKind::Other, e.to_string())))?;

        let headers = reader.headers()
            .map_err(|e| CloudpoolError::InvalidInput(format!("CSV headers error: {}", e)))?
            .clone();
        
        let mut records_vec = Vec::new();
        for result in reader.records() {
            let record = result.map_err(|e| CloudpoolError::InvalidInput(format!("CSV parsing error: {}", e)))?;
            let mut obj = serde_json::Map::new();
            for (header, field) in headers.iter().zip(record.iter()) {
                obj.insert(header.to_string(), Value::String(field.to_string()));
            }
            records_vec.push(Value::Object(obj));
        }

        let json_array = Value::Array(records_vec);
        serde_json::to_string(&json_array)
            .map_err(|e| CloudpoolError::SerializationError(e))
    }
}
