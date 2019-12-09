# Hotspotter
Hotspotter is a microservice that receives keys that have been requested over time and stores counters using Redis.

Counters are stored in buckets for each hour, as a Redis sorted set (zset), and are aggregated upon request. 
Aggregation results are cached for a configurable time (default is 2 minutes) so that requests can be handled more efficiently.
Given that this service is intended to be consumed from a load balancer, this cache should be tuned according to the
expected responsiveness of the system to new hot keys.

## Setup and run Redis
See [Documentation](https://redis.io/topics/quickstart)

### Make redis from sources
```bash
wget http://download.redis.io/redis-stable.tar.gz
tar xvzf redis-stable.tar.gz
cd redis-stable
make
```

### Run a standalone local redis server
Just execute the "redis-server" script on the src:
```bash
redis-stable/src/redis-server
```

## Run application
You can run the application on the default (dev) environment by executing:
```bash
sbt run
```

To specify another enviroment (such as prod), use the following command:
```bash
sbt '; set javaOptions += "-Denvironment=prod"; run'
```

The application will run on port 9290 by default.

## Application Resources

### Key Hits
You can post several key hits at a time: 
```code
POST /key-hits
```
with a body that follows the structure:
```json
{
	"hits": [
		{ "key": "1234" },
		{ "key": "5678" }
	]
}
```
You call this service as "fire and forget". The keys will be processed asynchronously.

### List key hotspots
You can retrieve a list of the hotspots of the previous H hours (H is configurable):
```code
GET /hotspots
``` 
The response will contain a list of the top N keys (N is configurable) sorted by request count descending:
```json
{
	"keys": ["1234", "5678", "..."]
}
```

### Check if a given key is a hotspot
````code
GET /hotspots/:key
````
#### Response if the key is a hotspot
```code
Status: 204 No Content
```

#### Response if the key is not a hotspot
```code
Status: 404 Not Found
```


## Future Work

* Local cache of last hotspots to respond in case of a failure on Redis cluster
* Store a local oplog file and recover from it in case of a failure writing the keys to Redis.
* Performance/Stress tests using Gatling.
* Enable configuration of custom buckets instead of fixed hour buckets.
* Add a way to post summarized hit counts instead of having to repeat the keys.
* Figure out how to get rid of the annoying security pop-ups when running the tests with the embedded Redis.


