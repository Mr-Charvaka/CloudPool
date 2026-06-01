use tokio_util::codec::{Decoder, Encoder};
use bytes::{BytesMut, Buf, BufMut, Bytes};
use crate::{Frame, FrameHeader, HEADER_SIZE, ProtocolError};
use crate::frame_type::FrameType;

pub struct DrapCodec;

impl Decoder for DrapCodec {
    type Item = Frame;
    type Error = ProtocolError;

    fn decode(&mut self, src: &mut BytesMut) -> Result<Option<Self::Item>, Self::Error> {
        if src.len() < HEADER_SIZE {
            // Wait for more data
            return Ok(None);
        }

        // Peek at length (3 bytes)
        let mut len_bytes = [0u8; 4];
        len_bytes[1..4].copy_from_slice(&src[0..3]);
        let length = u32::from_be_bytes(len_bytes) as usize;

        if src.len() < HEADER_SIZE + length {
            // Reserve enough space for the full frame
            src.reserve(HEADER_SIZE + length - src.len());
            return Ok(None);
        }

        // We have a full frame. Advance and parse.
        src.advance(3); // Skip length

        let frame_type_raw = src.get_u8();
        let frame_type = FrameType::try_from(frame_type_raw)
            .map_err(|_| ProtocolError::InvalidFrameType(frame_type_raw))?;

        let flags = src.get_u8();
        let stream_id = src.get_u32();

        let mut payload = src.split_to(length).freeze();

        // Handle PADDED flag (Section 9.2)
        if flags & 0x04 != 0 {
            if payload.is_empty() { return Err(ProtocolError::InsufficientData); }
            let mut payload_mut = BytesMut::from(payload);
            let pad_len = payload_mut.get_u8() as usize;
            if payload_mut.len() < pad_len { return Err(ProtocolError::InsufficientData); }
            payload_mut.truncate(payload_mut.len() - pad_len);
            payload = payload_mut.freeze();
        }

        Ok(Some(Frame {
            header: FrameHeader {
                length: length as u32,
                frame_type,
                flags,
                stream_id,
            },
            payload,
        }))
    }
}

impl Encoder<Frame> for DrapCodec {
    type Error = std::io::Error;

    fn encode(&mut self, item: Frame, dst: &mut BytesMut) -> Result<(), Self::Error> {
        // Reserve space for header + payload
        dst.reserve(HEADER_SIZE + item.payload.len());

        let len_bytes = item.header.length.to_be_bytes();
        dst.put_slice(&len_bytes[1..4]); // 24-bit length
        
        dst.put_u8(item.header.frame_type as u8);
        dst.put_u8(item.header.flags);
        dst.put_u32(item.header.stream_id);

        if item.header.flags & 0x04 != 0 {
            // Padding logic (Section 9.2)
            let pad_len = 0u8; // In production, calculate random padding
            dst.put_u8(pad_len);
            dst.put_slice(&item.payload);
            for _ in 0..pad_len { dst.put_u8(0); }
        } else {
            dst.put_slice(&item.payload);
        }

        Ok(())
    }
}
