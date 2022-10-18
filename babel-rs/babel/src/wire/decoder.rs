use std::io::Cursor;

use tokio::io::{AsyncRead, AsyncReadExt};

use crate::Deserialize;

use super::{ControlMessage, Message, MessageCode};

#[derive(Debug)]
enum State {
    ReadingSize { read: usize },
    ReadingCode,
    ReadingPayload { read: usize, code: MessageCode },
}

impl Default for State {
    fn default() -> Self {
        Self::ReadingSize { read: 0 }
    }
}

pub struct Decoder<R> {
    buffer: Vec<u8>,
    state: State,
    reader: R,
}

impl<R> Decoder<R> {
    pub fn new(reader: R) -> Self {
        Self {
            buffer: Default::default(),
            state: Default::default(),
            reader,
        }
    }

    pub fn into_inner(self) -> R {
        self.reader
    }
}

impl<R> Decoder<R>
where
    R: AsyncRead + Unpin,
{
    pub async fn decode<M>(&mut self) -> std::io::Result<Message<M>>
    where
        M: Deserialize,
    {
        loop {
            match self.state {
                State::ReadingSize { ref mut read } => {
                    if *read == 4 {
                        let size = i32::from_be_bytes(self.buffer[..4].try_into().unwrap()) - 1;
                        let size = usize::try_from(size).unwrap();
                        log::trace!("read size = {size}");
                        self.buffer.resize(size, 0);
                        self.state = State::ReadingCode;
                        continue;
                    }
                    let missing = 4 - *read;
                    self.buffer.resize(4, 0);
                    *read += self.reader.read(&mut self.buffer[..missing]).await?;
                }
                State::ReadingCode => {
                    let code = self.reader.read_i8().await?;
                    log::trace!("read code i8 = {code}");
                    let code = MessageCode::from_i8(code).expect("invalid message code received");
                    log::trace!("read code = {code:?}");
                    self.state = State::ReadingPayload { read: 0, code };
                    continue;
                }
                State::ReadingPayload { ref mut read, code } => {
                    log::trace!("reading payload {} / {}", read, self.buffer.len());
                    if *read == self.buffer.len() {
                        let cursor = Cursor::new(&self.buffer);
                        let msg = match code {
                            MessageCode::Control => {
                                Message::Control(ControlMessage::deserialize(cursor)?)
                            }
                            MessageCode::Application => {
                                Message::Application(M::deserialize(cursor)?)
                            }
                        };
                        self.state = State::default();
                        return Ok(msg);
                    }
                    let missing = self.buffer.len() - *read;
                    let n = self
                        .reader
                        .read(&mut self.buffer[*read..*read + missing])
                        .await?;
                    if n == 0 {
                        return Err(std::io::Error::new(
                            std::io::ErrorKind::UnexpectedEof,
                            "while reading message payload",
                        ));
                    }
                    *read += n;
                }
            }
        }
    }
}
