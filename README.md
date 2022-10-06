# This program demonstrates how to execute a lua script from Java (using Jedis)

### You will need a redis instance that has the RedisJSON module installed to run this successfully
### This program does not expect to authenticate the user (that part I haven't tested)

* To run the program execute the following replacing host and port values with your own:

```
mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000"
```

### Sample output from a successful run of the program 
### <em>(note the latency_measure values in the samples shows the microseconds spent adding a new group object to the json object in Redis)</em>

```
Connecting to 192.168.1.21:12000

Here is the JSON object retrieved from redis:
{groupCount=2.0, name=Practitioner1, groups={grp1={name=gn1, groupId=gid1, groupUID=UID:1}, grp2={name=gn2, groupId=gid2, groupUID=UID:2}}}

Here are some latency samples from the beginning of the stream:

XRANGE samples: latency_measure: 88 groupCount: 3
XRANGE samples: latency_measure: 33 groupCount: 4
XRANGE samples: latency_measure: 26 groupCount: 5
XRANGE samples: latency_measure: 27 groupCount: 6
XRANGE samples: latency_measure: 59 groupCount: 7

And now some samples from the end of the stream...

XREVRANGE samples: latency_measure: 23 groupCount: 20002
XREVRANGE samples: latency_measure: 24 groupCount: 20001
XREVRANGE samples: latency_measure: 21 groupCount: 20000
XREVRANGE samples: latency_measure: 22 groupCount: 19999
XREVRANGE samples: latency_measure: 22 groupCount: 19998

Proving the JSON is well formed:
Retrieving a nested group object: 
        [{"groupId":"gid1812","name":"gn1812","groupUID":"UID:1812"}]

```
