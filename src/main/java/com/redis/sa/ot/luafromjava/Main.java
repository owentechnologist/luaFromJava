package com.redis.sa.ot.luafromjava;

import org.json.JSONObject;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.resps.StreamEntry;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 *  * To run the program execute the following replacing host and port values with your own:
 *  * mvn compile exec:java -Dexec.cleanupDaemonThreads=false -Dexec.args="--host 192.168.1.21 --port 12000"
 */
public class Main {

    private static HostAndPort hnp = new HostAndPort("192.168.1.20",12000);

    public static void main(String[] args) {
        String host = "192.168.1.20";
        Integer port = 12000;
        String username = "default";
        String password = "";
        if(args.length>0){
            ArrayList<String> argList = new ArrayList<>(Arrays.asList(args));
            if(argList.contains("--host")){
                int hostIndex = argList.indexOf("--host");
                host = argList.get(hostIndex+1);
            }
            if(argList.contains("--port")){
                int portIndex = argList.indexOf("--port");
                port = Integer.parseInt(argList.get(portIndex+1));
            }
            if(argList.contains("--username")){
                int userNameIndex = argList.indexOf("--username");
                username = argList.get(userNameIndex+1);
            }
            if(argList.contains("--password")){
                int passwordIndex = argList.indexOf("--password");
                password = argList.get(passwordIndex + 1);
            }
        }
        HostAndPort hnp = new HostAndPort(host,port);
        System.out.println("Connecting to "+hnp.toString());
        URI uri = null;
        try {
            if(!("".equalsIgnoreCase(password))){
                uri = new URI("redis://" + username + ":" + password + "@" + hnp.getHost() + ":" + hnp.getPort());
            }else{
                uri = new URI("redis://" + hnp.getHost() + ":" + hnp.getPort());
            }
        }catch(URISyntaxException use){use.printStackTrace();System.exit(1);}
        try (UnifiedJedis jedis = new UnifiedJedis(uri)) {
            //cleanup old keys:
            jedis.del("jo{g1}");
            jedis.del("timeTrackStream{g1}");

            //create the baseline JSON object in redis:
            /* looks like this:
            {\"name\":\"Practitioner1\",
            \"groupCount\":2,
            \"groups\":
            {\"grp1\":{\"name\":\"gn1\",\"groupId\":\"gid1\",\"groupUID\":\"UID:1\"},
            \"grp2\":{\"name\":\"gn2\",\"groupId\":\"gid2\",\"groupUID\":\"UID:2\"}
             */
            JSONObject obj = new JSONObject();
            obj.put("name", "Practitioner1");
            obj.put("groupCount", 2);
            JSONObject groups = new JSONObject();
            HashMap<String, String> grpdata = new HashMap<>();
            grpdata.put("name", "gn1");
            grpdata.put("groupId", "gid1");
            grpdata.put("groupUID", "UID:1");
            groups.put("grp1", grpdata);

            HashMap<String, String> grpdata2 = new HashMap<>();
            grpdata2.put("name", "gn2");
            grpdata2.put("groupId", "gid2");
            grpdata2.put("groupUID", "UID:2");
            groups.put("grp2", grpdata2);
            obj.put("groups", groups);
            jedis.jsonSet("jo{g1}", obj);

            // retrieve and print out the stored JSON object for sanity check:
            System.out.println("\nHere is the JSON object retrieved from redis:\n"+jedis.jsonGet("jo{g1}"));

            // create the LUA script string for our use:
            String luaString = "for index = 1,20000 do local startTime = redis.call('TIME') local groupCount =1+ (redis.call('JSON.OBJLEN', KEYS[1], '$.groups')[1]) local grpObjKey = '$.groups.grp'.. groupCount local grpObjVal = '{\\\"name\\\"\\: \\\"gn' ..groupCount.. '\\\",\\\"groupId\\\"\\: \\\"gid' ..groupCount.. '\\\",\\\"groupUID\\\"\\: \\\"UID\\:'..groupCount..'\\\"}' redis.call('JSON.SET', KEYS[1],'$.groupCount',groupCount) redis.call('JSON.SET', KEYS[1], grpObjKey,grpObjVal) local endTime = redis.call('TIME') local x = 0 if(endTime[2] - startTime[2] > 0) then x = (endTime[2] - startTime[2]) else x=(((endTime[1] - startTime[1])*1000000)-(1000000-startTime[2])+endTime[2]) end redis.call('XADD',KEYS[2],'*','latency_measure',x,'groupCount',groupCount,'startTime_millis',startTime[1],'endTime_millis',endTime[1]) end";
            // create the List of keynames to pass along with the lua script:
            ArrayList<String> keynames = new ArrayList<>();
            keynames.add("jo{g1}");
            keynames.add("timeTrackStream{g1}");
            // fire the lua script that adds 20,000 child objects to the JSON object:
            jedis.eval(luaString, keynames, new ArrayList<String>());

            // check the Stream for evidence of the latency experienced by LUA script:
            System.out.println("\nHere are some latency samples from the beginning of the stream:\n");
            List<StreamEntry> se = jedis.xrange("timeTrackStream{g1}", "-", "+", 5);
            Iterator<StreamEntry> streamEntryIterator = se.iterator();
            while (streamEntryIterator.hasNext()) {
                Map<String, String> seiMap = streamEntryIterator.next().getFields();
                System.out.println("XRANGE samples: latency_measure: " + seiMap.get("latency_measure") + " groupCount: " + seiMap.get("groupCount"));
            }
            System.out.println("\nAnd now some samples from the end of the stream...\n");
            // again: check the Stream for evidence of the latency experienced by LUA script:
            se = jedis.xrevrange("timeTrackStream{g1}", "+", "-", 5);
            streamEntryIterator = se.iterator();
            while (streamEntryIterator.hasNext()) {
                Map<String, String> seiMap = streamEntryIterator.next().getFields();
                System.out.println("XREVRANGE samples: latency_measure: " + seiMap.get("latency_measure") + " groupCount: " + seiMap.get("groupCount"));
            }

            //retrieve and print out a nested group object to prove the JSON Object is well-formed:
            Path2 path = new Path2("$.groups.grp1812");

            System.out.println("\nProving the JSON is well formed:\nRetrieving a nested group object: \n\t"+jedis.jsonGet("jo{g1}",path));

        }
    }
}
