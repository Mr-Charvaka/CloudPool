#[derive(Debug, Clone, Copy, PartialEq, Eq)]
#[repr(u8)]
pub enum FrameType {
    Data = 0x00,
    Headers = 0x01,
    RstStream = 0x02,
    Settings = 0x03,
    Ping = 0x04,
    Pong = 0x05,
    WindowUpdate = 0x06,
    AuthReq = 0x07,
    AuthOk = 0x08,
    AuthFail = 0x09,
    TunnelReq = 0x0A,
    TunnelCreated = 0x0B,
    TunnelDenied = 0x0C,
    TunnelClose = 0x0D,
    TunnelClosedAck = 0x0E,
    StreamOpen = 0x0F,
    StreamClose = 0x10,
    Error = 0x11,
    GoAway = 0x12,
    Replay = 0x14,
    UdpData = 0x15,
}

impl TryFrom<u8> for FrameType {
    type Error = u8;

    fn try_from(value: u8) -> Result<Self, <Self as TryFrom<u8>>::Error> {
        match value {
            0x00 => Ok(FrameType::Data),
            0x01 => Ok(FrameType::Headers),
            0x02 => Ok(FrameType::RstStream),
            0x03 => Ok(FrameType::Settings),
            0x04 => Ok(FrameType::Ping),
            0x05 => Ok(FrameType::Pong),
            0x06 => Ok(FrameType::WindowUpdate),
            0x07 => Ok(FrameType::AuthReq),
            0x08 => Ok(FrameType::AuthOk),
            0x09 => Ok(FrameType::AuthFail),
            0x0A => Ok(FrameType::TunnelReq),
            0x0B => Ok(FrameType::TunnelCreated),
            0x0C => Ok(FrameType::TunnelDenied),
            0x0D => Ok(FrameType::TunnelClose),
            0x0E => Ok(FrameType::TunnelClosedAck),
            0x0F => Ok(FrameType::StreamOpen),
            0x10 => Ok(FrameType::StreamClose),
            0x11 => Ok(FrameType::Error),
            0x12 => Ok(FrameType::GoAway),
            0x14 => Ok(FrameType::Replay),
            0x15 => Ok(FrameType::UdpData),
            v => Err(v),
        }
    }
}
