/**
 *  Leviton
 *
 *  Copyright 2017 SM TM
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

metadata {
    definition (name: "Leviton Motion Sensor", namespace: "Subu Muthu", author: "Subu Muthu") {

        
		capability "Motion Sensor"
		capability "Configuration"
		capability "Battery"
		capability "Refresh"
        capability "Sensor"
        capability "Health Check"
        
        command "enrollResponse"

		fingerprint endpointId: "01", profileId: "0104", deviceId: "0107", deviceVersion: "00", inClusters: " 0000,0001,0003,0020,0406 "

       // fingerprint profileId: "0104", inClusters: "0000,0001,0003,0406,0400,0402", outClusters: "0019", manufacturer: "Philips", model: "SML001", deviceJoinName: "Hue Motion Sensor"
    }

	preferences {
    		section {
			input title: "Temperature Offset", description: "This feature allows you to correct any temperature variations by selecting an offset. Ex: If your sensor consistently reports a temp that's 5 degrees too warm, you'd enter '-5'. If 3 degrees too cold, enter '+3'.", displayDuringSetup: false, type: "paragraph", element: "paragraph"
			input "tempOffset", "number", title: "Degrees", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
		}
    }

    tiles(scale: 2) {
		multiAttributeTile(name:"motion", type: "generic", width: 6, height: 4){
			tileAttribute ("device.motion", key: "PRIMARY_CONTROL") {
				attributeState "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
				attributeState "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
			}
		}
        
        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "battery", label:'${currentValue}% battery', unit:""
		}

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"configure", action:"configure"
        }
        main "motion"
        details(["motion","battery", "refresh",'configure'])
    }
}



// Parse incoming device messages to generate events
def parse(String description) {
    def msg = zigbee.parse(description)
    
  //  log.warn "--"
  //  log.trace description
   // log.debug msg
   // def x = zigbee.parseDescriptionAsMap( description )
   // log.error x
    
	Map map = [:]
    if (description?.startsWith('catchall:')) {
		map = parseCatchAllMessage(description)
	}

//	else if (description?.startsWith('zone status')) {
//		//map = parseIasMessage(description)
//        log.trace "zone status"
//	}

	def result = map ? createEvent(map) : null

	if (description?.startsWith('enroll request')) {
		List cmds = enrollResponse()
		result = cmds?.collect { new physicalgraph.device.HubAction(it) }
	}
	else if (description?.startsWith('read attr -')) {
		result = parseReportAttributeMessage(description).each { createEvent(it) }
       //  log.debug "Reading attributes..."
	}
	return result
}

/*
  Refresh Function
*/
def refresh() {
    log.debug "Refreshing Values"

    def refreshCmds = []
    refreshCmds +=zigbee.readAttribute(0x0001, 0x0020) // Read battery?
  //  refreshCmds += zigbee.readAttribute(0x0402, 0x0000) // Read temp?
   // refreshCmds += zigbee.readAttribute(0x0400, 0x0000) // Read luminance?
  // refreshCmds +=zigbee.readAttribute(0x0001, 0x0021) // Read battery?
   refreshCmds += zigbee.readAttribute(0x0406, 0x0000) // Read motion?

    return refreshCmds + enrollResponse()

    }
/*
  Configure Function
*/
def configure() {

// TODO : device watch?

	String zigbeeId = swapEndianHex(device.hub.zigbeeId)
	log.debug "Confuguring Reporting and Bindings."
    
    
	def configCmds = []
   // configCmds += zigbee.batteryConfig()
    
    configCmds += zigbee.configureReporting(0x0001, 0x0020, 0x20, 30, 1800, 0x01)
    
    configCmds += zigbee.configureReporting(0x406,0x0000, 0x18, 10, 1800, null) // motion // confirmed
    
    
    // Data type is not 0x20 = 0x8D invalid data type Unsigned 8-bit integer
    
//	configCmds += zigbee.configureReporting(0x0001, 0x0021, 0x20,DataType.UINT8, 30, 1800, 0x01)    
    
    return refresh() + configCmds 
}

/*
	getMotionResult
 */

private Map getMotionResult(value) {
    log.trace "Motion : " + value
	
    def descriptionText = value == "01" ? '{{ device.displayName }} detected motion':
			'{{ device.displayName }} stopped detecting motion'
    
    return [
		name: 'motion',
		value: value == "01" ? "active" : "inactive",
		descriptionText: descriptionText,
		translatable: true,
	]
}



/*
	getBatteryResult
*/
//TODO: needs calibration
private Map getBatteryResult(rawValue) {
	log.debug "Battery rawValue = ${rawValue}"

	def result = [
		name: 'battery',
		value: '--',
		translatable: true
	]

	def volts = rawValue / 10

	if (rawValue == 0 || rawValue == 255) {}
	else {
		if (volts > 3.5) {
			result.descriptionText = "{{ device.displayName }} battery has too much power: (> 3.5) volts."
		} else {
            	log.debug "Battery volts = ${volts} v"
				def minVolts = 2.1
				def maxVolts = 3.0
				def pct = (volts - minVolts) / (maxVolts - minVolts)
				def roundedPct = Math.round(pct * 100)
				if (roundedPct <= 0)
					roundedPct = 1
				result.value = Math.min(100, roundedPct)
				result.descriptionText = "{{ device.displayName }} battery was {{ value }}%"
                //result.value = volts
			}
	}

	return result
}


/*
	parseReportAttributeMessage
*/
private List parseReportAttributeMessage(String description) {
	Map descMap = (description - "read attr - ").split(",").inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
	}

	List result = []
    
    
    // Motion
   	if (descMap.cluster == "0406" && descMap.attrId == "0000") {
    	result << getMotionResult(descMap.value)
        //log.debug "Motion detectionresult:  ${result}"
	}
    
    // Battery
    else if (descMap.cluster == "0001" && descMap.attrId == "0020") {
		result << getBatteryResult(Integer.parseInt(descMap.value, 16))
      //   log.debug "Battery  detection rawvalue:  ${value}"
      //   log.debug "Battery  detection result:  ${result}"
	}
    
   else if (descMap.cluster == "0001" && descMap.attrId == "0021") {
		// result << getBatteryResult(Integer.parseInt(descMap.value, 16))
                  log.debug "Battery  detection % :  ${descMap.value}"
	 }

	return result
}


/*
	parseCatchAllMessage
*/
private Map parseCatchAllMessage(String description) {
	Map resultMap = [:]
	def cluster = zigbee.parse(description)
	log.debug cluster
	if (shouldProcessMessage(cluster)) {
		switch(cluster.clusterId) {
			case 0x0001:
				// 0x07 - configure reporting
				if (cluster.command != 0x07) {
					resultMap = getBatteryResult(cluster.data.last())
				}
			break

		}
	}

	return resultMap
}

private boolean shouldProcessMessage(cluster) {
	// 0x0B is default response indicating message got through
	boolean ignoredMessage = cluster.profileId != 0x0104 ||
	cluster.command == 0x0B ||
	(cluster.data.size() > 0 && cluster.data.first() == 0x3e)
	return !ignoredMessage
}


// This seems to be IAS Specific and not needed we are not really a motion sensor
def enrollResponse() {
//	log.debug "Sending enroll response"
//	String zigbeeEui = swapEndianHex(device.hub.zigbeeEui)
//	[
//		//Resending the CIE in case the enroll request is sent before CIE is written
//		"zcl global write 0x500 0x10 0xf0 {${zigbeeEui}}", "delay 200",
//		"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 500",
//		//Enroll Response
//		"raw 0x500 {01 23 00 00 00}", "delay 200",
//		"send 0x${device.deviceNetworkId} 1 1", "delay 200"
//	]
}

def configureHealthCheck() {
    Integer hcIntervalMinutes = 12
    refresh()
    sendEvent(name: "checkInterval", value: hcIntervalMinutes * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
}

def updated() {
    log.debug "in updated()"
    configureHealthCheck()
}

def ping() {
    return zigbee.onOffRefresh()
}





private getEndpointId() {
	new BigInteger(device.endpointId, 16).toString()
}

private String swapEndianHex(String hex) {
    reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
    int i = 0;
    int j = array.length - 1;
    byte tmp;
    while (j > i) {
        tmp = array[j];
        array[j] = array[i];
        array[i] = tmp;
        j--;
        i++;
    }
    return array
}