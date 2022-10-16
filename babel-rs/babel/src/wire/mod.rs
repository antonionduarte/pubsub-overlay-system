use bytes::Bytes;

use crate::{
    protocol::{ProtocolID, ProtocolMessageID},
    Deserialize, Properties, Serialize,
};

mod decoder;
mod encoder;

pub use decoder::*;
pub use encoder::*;

const CONTROL_MAGIC_NUMBER: i32 = 0x79676472;

const MESSAGE_CODE_CONTROL: i8 = 0;
const MESSAGE_CODE_APPLICATION: i8 = 1;

const OPCODE_HEARBEAT: i32 = 0;
const OPCODE_FIRST_HANDSHAKE: i32 = 1;
const OPCODE_SECOND_HANDSHAKE: i32 = 2;
const OPCODE_INVALID_ATTRIBUTE: i32 = 3;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
enum MessageCode {
    Control,
    Application,
}

pub enum Message<M> {
    Control(ControlMessage),
    Application(M),
}

#[derive(Debug, Clone)]
pub enum ControlMessage {
    Heartbeat,
    FirstHandshake(Handshake),
    SecondHandshake(Handshake),
    InvalidAttribute(InvalidAttribute),
}

#[derive(Debug, Clone)]
pub struct Handshake {
    pub magic_number: i32,
    pub properties: Properties,
}

#[derive(Debug, Clone)]
pub struct InvalidAttribute {
    pub magic_number: i32,
}

#[derive(Debug, Clone)]
pub struct BabelMessage {
    pub source_protocol: ProtocolID,
    pub target_protocol: ProtocolID,
    pub message_id: ProtocolMessageID,
    pub message: Bytes,
}

impl MessageCode {
    fn to_i8(&self) -> i8 {
        match self {
            MessageCode::Control => MESSAGE_CODE_CONTROL,
            MessageCode::Application => MESSAGE_CODE_APPLICATION,
        }
    }

    fn from_i8(v: i8) -> Option<Self> {
        match v {
            _ if v == MESSAGE_CODE_CONTROL => Some(Self::Control),
            _ if v == MESSAGE_CODE_APPLICATION => Some(Self::Application),
            _ => None,
        }
    }
}

impl<M> Clone for Message<M>
where
    M: Clone,
{
    fn clone(&self) -> Self {
        match self {
            Self::Control(arg0) => Self::Control(arg0.clone()),
            Self::Application(arg0) => Self::Application(arg0.clone()),
        }
    }
}
impl<M> std::fmt::Debug for Message<M>
where
    M: std::fmt::Debug,
{
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            Self::Control(arg0) => f.debug_tuple("Control").field(arg0).finish(),
            Self::Application(arg0) => f.debug_tuple("Application").field(arg0).finish(),
        }
    }
}

impl ControlMessage {
    fn opcode(&self) -> i32 {
        match self {
            ControlMessage::Heartbeat => OPCODE_HEARBEAT,
            ControlMessage::FirstHandshake(_) => OPCODE_FIRST_HANDSHAKE,
            ControlMessage::SecondHandshake(_) => OPCODE_SECOND_HANDSHAKE,
            ControlMessage::InvalidAttribute(_) => OPCODE_INVALID_ATTRIBUTE,
        }
    }
}

impl Serialize for ControlMessage {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: bytes::BufMut,
    {
        buf.put_i32(self.opcode());
        match self {
            ControlMessage::Heartbeat => Ok(()),
            ControlMessage::FirstHandshake(hs) | ControlMessage::SecondHandshake(hs) => {
                hs.serialize(&mut buf)
            }
            ControlMessage::InvalidAttribute(ia) => ia.serialize(&mut buf),
        }
    }
}

impl Deserialize for ControlMessage {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: bytes::Buf,
    {
        let opcode = buf.get_i32();
        match opcode {
            OPCODE_HEARBEAT => Ok(Self::Heartbeat),
            OPCODE_FIRST_HANDSHAKE => Ok(Self::FirstHandshake(Handshake::deserialize(buf)?)),
            OPCODE_SECOND_HANDSHAKE => Ok(Self::SecondHandshake(Handshake::deserialize(buf)?)),
            OPCODE_INVALID_ATTRIBUTE => {
                Ok(Self::InvalidAttribute(InvalidAttribute::deserialize(buf)?))
            }
            _ => todo!(),
        }
    }
}

impl Serialize for BabelMessage {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: bytes::BufMut,
    {
        buf.put_i16(self.source_protocol.0);
        buf.put_i16(self.target_protocol.0);
        buf.put_i16(self.message_id.0);
        buf.put_slice(&self.message);
        Ok(())
    }
}

impl Deserialize for BabelMessage {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: bytes::Buf,
    {
        Ok(Self {
            source_protocol: ProtocolID(buf.get_i16()),
            target_protocol: ProtocolID(buf.get_i16()),
            message_id: ProtocolMessageID(buf.get_i16()),
            message: buf.copy_to_bytes(buf.remaining()),
        })
    }
}

impl Handshake {
    pub fn new(properties: Properties) -> Self {
        Self {
            magic_number: CONTROL_MAGIC_NUMBER,
            properties,
        }
    }
}

impl Serialize for Handshake {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: bytes::BufMut,
    {
        buf.put_i32(self.magic_number);
        self.properties.serialize(buf)
    }
}

impl Deserialize for Handshake {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: bytes::Buf,
    {
        let magic_number = buf.get_i32();
        let properties = Properties::deserialize(&mut buf)?;
        Ok(Handshake {
            magic_number,
            properties,
        })
    }
}

impl Serialize for InvalidAttribute {
    fn serialize<B>(&self, mut buf: B) -> std::io::Result<()>
    where
        B: bytes::BufMut,
    {
        buf.put_i32(self.magic_number);
        Ok(())
    }
}

impl Deserialize for InvalidAttribute {
    fn deserialize<B>(mut buf: B) -> std::io::Result<Self>
    where
        B: bytes::Buf,
    {
        let magic_number = buf.get_i32();
        Ok(Self { magic_number })
    }
}
