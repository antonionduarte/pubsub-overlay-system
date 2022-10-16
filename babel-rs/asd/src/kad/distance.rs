use super::KadID;

#[derive(Debug, PartialEq, Eq, PartialOrd, Ord, Hash)]
pub struct Distance([u8; KadID::LENGTH]);

impl Distance {
    const LENGTH: usize = KadID::LENGTH;

    pub fn new(distance: [u8; Self::LENGTH]) -> Self {
        Self(distance)
    }

    pub fn from_ids(id1: &KadID, id2: &KadID) -> Self {
        id1.distance(id2)
    }
}
