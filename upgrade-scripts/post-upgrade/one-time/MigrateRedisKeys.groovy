void call() {
    // Read the Redis password from the redis-auth secret
    String redisPassword = sh(script: "oc get secret redis-auth -o jsonpath={.data.password} -n ${NAMESPACE} | base64 --decode", returnStdout: true)

    // Execute redis request "set key value" on pod rfr-redis-sentinel-0
    String response = executeRedisCommand("rfr-redis-sentinel-0", redisPassword, "set key value ex 60")

    // Check the response and save the appropriate pod name to a variable
    String redisPod = response.contains("OK") ? "rfr-redis-sentinel-0" : "rfr-redis-sentinel-1"

    // Connect to the redisPod and retrieve all keys with pattern bpm-form-submissions:*
    String redisKeys = executeRedisCommand(redisPod, redisPassword, "keys 'bpm-form-submissions:*'")

    // Split the keys into a list and extract the key from each line
    List<String> keysList = redisKeys.split('\n')

    println("Keys list: ${keysList}")

    // Loop over the keys
    keysList.each { key ->
        // Check if key fits pattern process/* (is process form data)
        if (key =~ /bpm-form-submissions:process\/.*/) {
            // Take a substring between first 2 / symbols in key, to follow pattern bpm-form-submissions:process/{processInstanceId}/task/{taskDefinitionKey}
            String processInstanceId = key.split('/')[1]

            // Execute on redis command sadd bpm-form-submissions:process-instance-id:{process instance id} {redis key}
            executeRedisCommand(redisPod, redisPassword, "sadd bpm-form-submissions:process-instance-id:${processInstanceId} ${key}")

            // Log the operation
            println("Processed a key with pattern process/*: ${key}")
        }
        // Check if key fits pattern lowcode_* (is process system signatures)
        else if (key =~ /bpm-form-submissions:lowcode_.*/) {
            // Take a substring between first 2 _ symbols in key, to follow pattern bpm-form-submissions:lowcode_{rootProcessInstanceId}_{processInstanceId}_system_signature_ceph_key
            String processInstanceId = key.split('_')[1]

            // Execute on redis command sadd bpm-form-submissions:process-instance-id:{process instance id} {redis key}
            executeRedisCommand(redisPod, redisPassword, "sadd bpm-form-submissions:process-instance-id:${processInstanceId} ${key}")

            // Log the operation
            println("Processed a key with pattern lowcode_*: ${key}")
        }
    }
}

String executeRedisCommand(String redisPod, String redisPassword, String command) {
    return sh(script: "set +x; oc exec ${redisPod} -n ${NAMESPACE} -- redis-cli -a ${redisPassword} ${command}", returnStdout: true).trim()
}

return this;
