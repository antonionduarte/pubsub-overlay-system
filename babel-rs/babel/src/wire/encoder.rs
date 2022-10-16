use tokio::io::{AsyncWrite, AsyncWriteExt};

use crate::Serialize;

use super::{ControlMessage, Message, MessageCode};

pub struct Encoder<W, M> {
    buffer: Vec<u8>,
    writer: W,
    _phatom: std::marker::PhantomData<M>,
}

impl<W, M> Encoder<W, M> {
    pub fn new(writer: W) -> Self {
        Self {
            buffer: Default::default(),
            writer,
            _phatom: std::marker::PhantomData,
        }
    }

    pub fn into_inner(self) -> W {
        self.writer
    }
}

impl<W, M> Encoder<W, M>
where
    W: AsyncWrite + Unpin,
    M: Serialize,
{
    pub async fn encode(&mut self, message: &Message<M>) -> std::io::Result<()> {
        match message {
            Message::Control(ref msg) => self.control(msg).await,
            Message::Application(ref msg) => self.application(msg).await,
        }
    }

    pub async fn control(&mut self, message: &ControlMessage) -> std::io::Result<()> {
        self.write(MessageCode::Control, message).await
    }

    pub async fn application(&mut self, message: &M) -> std::io::Result<()> {
        self.write(MessageCode::Application, message).await
    }

    async fn write(&mut self, code: MessageCode, message: &impl Serialize) -> std::io::Result<()> {
        self.buffer.clear();
        message.serialize(&mut self.buffer)?;
        self.writer
            .write_i32(i32::try_from(self.buffer.len() + 1).unwrap())
            .await?;
        self.writer.write_i8(code.to_i8()).await?;
        self.writer.write_all(&self.buffer).await?;
        self.writer.flush().await?;
        Ok(())
    }
}
