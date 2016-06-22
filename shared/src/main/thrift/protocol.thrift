namespace java oy.protocol
#@namespace scala oy.protocol

exception WriteException {}
exception ReadException {}

struct Oy {
    1: i64 id,
    2: i32 from_id,
    3: i32 to_id,
    4: i64 timestamp,
    5: string message
}
