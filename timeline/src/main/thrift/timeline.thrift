namespace java oy.storage
#@namespace scala oy.storage

include "protocol.thrift"


struct TimelineResponse {
    1: list<protocol.Oy> oys
}

service TimelineService {
    TimelineResponse globalTimeline()
    TimelineResponse timelineForUser(i32 userId)
    bool postOy(protocol.Oy yo)
}
