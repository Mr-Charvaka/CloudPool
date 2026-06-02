pub mod frame_type;
pub mod codec;

use bytes::{Buf, BufMut, Bytes};
use frame_type::FrameType;
use std::fmt;

pub const HEADER_SIZE: usize = 9;
pub const DRAP_MAGIC: &[u8; 4] = b"DRAP";
pub const PROTOCOL_VERSION: u16 = 1;
pub const DEFAULT_WINDOW_SIZE: u32 = 65535;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FrameHeader {
    pub length: u32, // 24-bit length
    pub frame_type: FrameType,
    pub flags: u8,
    pub stream_id: u32,
}

#[derive(Clone, PartialEq, Eq)]
pub struct Frame {
    pub header: FrameHeader,
    pub payload: Bytes,
}

impl fmt::Debug for Frame {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_struct("Frame")
            .field("header", &self.header)
            .field("payload_size", &self.payload.len())
            .finish()
    }
}

#[derive(thiserror::Error, Debug)]
pub enum ProtocolError {
    #[error("Invalid frame type: {0}")]
    InvalidFrameType(u8),
    #[error("Frame too large: {0}")]
    FrameTooLarge(u32),
    #[error("Insufficient data")]
    InsufficientData,
}

impl Frame {
    pub fn new(frame_type: FrameType, flags: u8, stream_id: u32, payload: Bytes) -> Self {
        Self {
            header: FrameHeader {
                length: payload.len() as u32,
                frame_type,
                flags,
                stream_id,
            },
            payload,
        }
    }

    pub fn encode(&self) -> Bytes {
        let mut buf = bytes::BytesMut::with_capacity(HEADER_SIZE + self.payload.len());
        
        // Calculate Wire Length (following Stream ID)
        let wire_payload_len = if self.header.flags & 0x04 != 0 {
            // Padding logic: Choose random length 1..32
            let pad_len = (rand::random::<u8>() % 32) + 1;
            (self.payload.len() + 1 + pad_len as usize) as u32
        } else {
            self.payload.len() as u32
        };

        // Length (3 bytes / 24 bits) - Wire length, not logical
        let len_bytes = wire_payload_len.to_be_bytes();
        buf.put_slice(&len_bytes[1..4]); 
        
        // Type (1 byte)
        buf.put_u8(self.header.frame_type as u8);
        
        // Flags (1 byte)
        buf.put_u8(self.header.flags);
        
        // Stream ID (4 bytes)
        buf.put_u32(self.header.stream_id);
        
        // Payload with Padding
        if self.header.flags & 0x04 != 0 {
            let pad_len = (wire_payload_len - self.payload.len() as u32 - 1) as u8;
            buf.put_u8(pad_len);
            buf.put_slice(&self.payload);
            let padding = vec![0u8; pad_len as usize];
            buf.put_slice(&padding);
        } else {
            buf.put_slice(&self.payload);
        }
        
        buf.freeze()
    }

    pub fn decode(mut src: Bytes) -> Result<Self, ProtocolError> {
        if src.len() < HEADER_SIZE {
            return Err(ProtocolError::InsufficientData);
        }

        // Length (3 bytes)
        let mut len_bytes = [0u8; 4];
        len_bytes[1..4].copy_from_slice(&src.slice(0..3));
        let length = u32::from_be_bytes(len_bytes);
        src.advance(3);

        // Type (1 byte)
        let frame_type_raw = src.get_u8();
        let frame_type = FrameType::try_from(frame_type_raw)
            .map_err(|_| ProtocolError::InvalidFrameType(frame_type_raw))?;

        // Flags (1 byte)
        let flags = src.get_u8();

        // Stream ID (4 bytes)
        let stream_id = src.get_u32();

        if src.len() < length as usize {
            return Err(ProtocolError::InsufficientData);
        }

        let mut payload = src.split_to(length as usize);

        if flags & 0x04 != 0 {
            if payload.is_empty() { return Err(ProtocolError::InsufficientData); }
            let pad_len = payload.get_u8() as usize;
            if payload.len() < pad_len { return Err(ProtocolError::InsufficientData); }
            payload = payload.split_to(payload.len() - pad_len);
        }

        Ok(Self {
            header: FrameHeader {
                length,
                frame_type,
                flags,
                stream_id,
            },
            payload,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_frame_roundtrip() {
        let payload = Bytes::from("hello world");
        let frame = Frame::new(FrameType::Data, 0x01, 42, payload.clone());
        
        let encoded = frame.encode();
        assert_eq!(encoded.len(), HEADER_SIZE + payload.len());
        
        let decoded = Frame::decode(encoded).unwrap();
        assert_eq!(decoded.header.frame_type, FrameType::Data);
        assert_eq!(decoded.header.flags, 0x01);
        assert_eq!(decoded.header.stream_id, 42);
        assert_eq!(decoded.payload, payload);
    }
}
