from __future__ import annotations


class KadID:
    LENGTH: int = 20

    def __init__(self, id: int):
        self._id_int = id

    def cpl(self, other: KadID) -> int:
        xored = self ^ other
        return xored.leading_zeros()

    def leading_zeros(self) -> int:
        b = self._id_int.to_bytes(self.LENGTH, byteorder="big")
        lz = 0
        for byte in b:
            if byte == 0:
                lz += 8
            else:
                lz += 8 - len(bin(byte)[2:])
                break
        return lz

    def __repr__(self):
        return self._id_int.to_bytes(KadID.LENGTH, byteorder="big").hex()

    def __eq__(self, other) -> bool:
        if isinstance(other, KadID):
            return self._id_int == other._id_int
        return False

    def __hash__(self) -> int:
        return hash(self._id_int)

    def __lt__(self, other):
        if isinstance(other, KadID):
            return self._id_int < other._id_int
        return False

    def __xor__(self, other) -> KadID:
        if not isinstance(other, KadID):
            raise TypeError("Can only xor with other KadID")
        return KadID(self._id_int ^ other._id_int)

    @staticmethod
    def from_hex(id: str) -> KadID:
        id_bytes = bytes.fromhex(id)
        assert len(id_bytes) == KadID.LENGTH
        id_int = int.from_bytes(id_bytes, byteorder="big")
        return KadID(id_int)


if __name__ == "__main__":
    id1 = KadID.from_hex(5 * "0" + "1" + "0" * 34)
    id2 = KadID.from_hex("0" * 39 + "1")
    id3 = KadID.from_hex("0" * 39 + "3")
    print(id1.leading_zeros())
    print(id2.leading_zeros())
    print(id3.leading_zeros())
    print(id2.cpl(id3))
