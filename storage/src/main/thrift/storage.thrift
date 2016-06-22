namespace java oy.storage
#@namespace scala oy.storage

include "protocol.thrift"

struct StorageResponse {
    1: list<protocol.Oy> oys
}

service OyStore {
    StorageResponse multiget(list<i64> oyIds)
    bool store(protocol.Oy yo)
}

