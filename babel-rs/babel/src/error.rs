use thiserror::Error;

pub type Result<T, E = Error> = std::result::Result<T, E>;

#[derive(Debug, Error)]
enum ErrorInner {
    #[error("{0}")]
    Message(String),
    #[error(transparent)]
    Other(Box<dyn std::error::Error + Send + 'static>),
}

#[derive(Debug, Error)]
#[error(transparent)]
pub struct Error(ErrorInner);

impl Error {
    pub(crate) fn message(msg: impl Into<String>) -> Self {
        Self(ErrorInner::Message(msg.into()))
    }

    pub(crate) fn other(err: impl std::error::Error + Send + 'static) -> Self {
        Self(ErrorInner::Other(Box::new(err)))
    }
}
