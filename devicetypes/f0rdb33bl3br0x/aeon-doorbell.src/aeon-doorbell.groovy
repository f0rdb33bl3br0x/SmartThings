/*
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 *
 * Forked from a fork of https://github.com/robertvandervoort/SmartThings/tree/master/Aeon%20Doorbell
 *
 * Changelog:
 *  v 0.1 - initial push of device type, ability to change ring tone, volume and number of rings, basic set reporting
 * for use as a switch to trigger stuff, and ability to trigger as an alarm
 *  v 0.2 - added separate preference for alarm triggering so a different ringtone can be used for alarm VS doorbell. created
 *  separate test buttons for doorbell and alarm
 *  v 0.3 - added firmware version and checksum reporting as part of the config command, modifed the way the battery check works, may need to redress it later
 *  v 0.4 - added Music Capability functionality (playTrack).  added setRingtone command to change default ringtone.  added ability to test sounds on demand using a slider on the device page
 *  v 0.5 - improved the playTrack functionality
 *  v 0.6 - dimmer
 */

 metadata {
    definition (name: "Aeon Doorbell", namespace: "f0rdb33bl3br0x", author: "Liam Stewart") {
        capability "Actuator"
        capability "Alarm"
        capability "Battery"
        capability "Switch"
        capability "SwitchLevel"
        capability "Configuration"
        capability "Refresh"
        capability "Music Player"

        command "atest"
        command "btest"
        command "getbatt"
        command "setRingtone"

        fingerprint deviceId: "0x1005", inClusters: "0x5E,0x98"
    }
    // simulator metadata

    simulator {
        /*status "battery good": new physicalgraph.zwave.Zwave().securityV1.securityMessageEncapsulation().encapsulate(
                zwave.configurationV1.configurationReport(
                parameterNumber: 42, configurationValue: 0
                )
            ).incomingMessage()
        status "low battery alert": new physicalgraph.zwave.Zwave().securityV1.securityMessageEncapsulation().encapsulate(
                zwave.configurationV1.configurationReport(
                parameterNumber: 42, configurationValue: 255
                )
            ).incomingMessage()
        */
        reply "9881002001FF,9881002002": "command: 9881, payload: 002003FF"
        reply "988100200100,9881002002": "command: 9881, payload: 00200300"
        reply "9881002001FF,delay 3000,988100200100,9881002002": "command: 9881, payload: 00200300"
    }

    tiles (scale: 2) {
        multiAttributeTile(name:"alarm", type:"generic", width:6, height:4, canChangeIcon: true, canChangeBackground: true) {
            tileAttribute("device.alarm", key: "PRIMARY_CONTROL") {
                attributeState "off", label:'OFF', action:'alarm.on', icon:"st.alarm.alarm.alarm", backgroundColor:"#ffffff"
                attributeState "on", label:'RINGING', action:'alarm.off', icon:"st.alarm.alarm.alarm", backgroundColor:"#e86d13"
            }
        }
        standardTile("btest", "device.alarm", inactiveLabel: false, width: 2, height: 2)    {
            state "default", label:'bell', action:"btest", icon:"st.Electronics.electronics14"
        }
        standardTile("atest", "device.alarm", inactiveLabel: false, width: 2, height: 2)    {
            state "default", label:'alarm', action:"atest", icon:"st.Electronics.electronics14"
        }
        standardTile("off", "device.alarm", inactiveLabel: false, width: 2, height: 2) {
            state "default", label:'', action:"off", icon:"st.secondary.off"
        }
        valueTile("battery", "device.battery", decoration: "flat", width: 2, height: 2) {
            state "battery", label:'Battery', action:"getbatt", backgroundColors:[
                    [value: 0, color: "#FF0000"],
                    [value: 100, color: "#00FF00"]
                ]
        }
        standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
        }
        standardTile("refresh", "device.power", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:'', action:"refresh.refresh", icon:"st.secondary.refresh"
        }
        // TODO: fix label etc.
        valueTile("testSoundLabel", "device.battery", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "battery", label:'Test Sound:', unit:""
        }
        controlTile("testSoundSlider", "device.playTrack", "slider", width: 4, height: 2, inactiveLabel: false, range:"(1..99)") {
            state "testSound", action:"playTrack", backgroundColor: "#1e9cbb"
        }
        valueTile("volumeLabel", "device.level", inactiveLabel: true, height:2, width:2, decoration: "flat") {
            state "levelValue", label:'${currentValue}%', unit:"", backgroundColor: "#53a7c0"
        }
        controlTile("volumeSlider", "device.level", "slider", width: 4, height: 2, inactiveLabel: false, range:"(0..100)", backgroundColor:"#ffe71e") {
            state "level", action:"switch level.setLevel"
        }

        main "alarm"
        details(["alarm","btest","atest","off","testSoundLabel","testSoundSlider","volumeLabel","volumeSlider","battery","refresh","configure"])
    }

    preferences {
        input "debugOutput", "boolean",
            title: "Enable debug logging?",
            defaultValue: false,
            displayDuringSetup: true
        input "prefRingtone", "integer",
            title: "Doorbell tone:",
            description: "Pick the ringtone, 1-100",
            defaultValue: 1,
            range: "1..100",
            required: false,
            displayduringSetup: true
        input "prefAlarmtone", "integer",
            title: "Alarm tone:",
            description: "Pick the alarm tone, 1-100",
            defaultValue: 1,
            range: "1..100",
            required: false,
            displayduringSetup: true
        input "prefNumrings", "integer",
            title: "Ring repetitions:",
            description: "How many times to ring per push of the doorbell",
            defaultValue: 1,
            range: "1..100",
            required: false,
            displayduringSetup: true
    }
}

def updated() {
    state.debug = ("true" == debugOutput)
    if (state.sec && !isConfigured()) {
        // in case we miss the SCSR
        response(configure())
    }
}

def parse(String description) {
    def result = null
    if (description.startsWith("Err 106")) {
        state.sec = 0
        result = createEvent( name: "secureInclusion", value: "failed", isStateChange: true,
            descriptionText: "This sensor failed to complete the network security key exchange. If you are unable to control it via SmartThings, you must remove it from your network and add it again.")
    } else if (description != "updated") {
        def cmd = zwave.parse(description, [0x25: 1, 0x26: 1, 0x27: 1, 0x32: 3, 0x33: 3, 0x59: 1, 0x70: 1, 0x72: 2, 0x73: 1, 0x7A: 2, 0x82: 1, 0x85: 2, 0x86: 1])
        if (cmd) {
            result = zwaveEvent(cmd)
        }
    }
    // if (state.debug) log.debug "Parsed '${description}' to ${result.inspect()}"
    return result
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    def encapsulatedCommand = cmd.encapsulatedCommand([0x25: 1, 0x26: 1, 0x27: 1, 0x32: 3, 0x33: 3, 0x59: 1, 0x70: 1, 0x72: 2, 0x73: 1, 0x7A: 2, 0x82: 1, 0x85: 2, 0x86: 1])
    state.sec = 1
    // if (state.debug) log.debug "encapsulated: ${encapsulatedCommand}"
    if (encapsulatedCommand) {
        zwaveEvent(encapsulatedCommand)
    } else {
        log.warn "Unable to extract encapsulated cmd from $cmd"
        createEvent(descriptionText: cmd.toString())
    }
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityCommandsSupportedReport cmd) {
    response(configure())
}

def zwaveEvent(physicalgraph.zwave.commands.powerlevelv1.PowerlevelTestNodeReport cmd) {
    debug("===Power level test node report received=== ${device.displayName}: statusOfOperation: ${cmd.statusOfOperation} testFrameCount: ${cmd.testFrameCount} testNodeid: ${cmd.testNodeid}")
    def request = [
        physicalgraph.zwave.commands.powerlevelv1.PowerlevelGet()
    ]
    response(commands(request))
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionCommandClassReport cmd) {
    debug("---COMMAND CLASS VERSION REPORT V1--- ${device.displayName} has command class version: ${cmd.commandClassVersion} - payload: ${cmd.payload}")
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
    def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
    updateDataValue("fw", fw)
    debug("---VERSION REPORT V1--- ${device.displayName} is running firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}")
}

def zwaveEvent(physicalgraph.zwave.commands.firmwareupdatemdv2.FirmwareMdReport cmd) {
    debug("---FIRMWARE MD REPORT V2--- ${device.displayName} has Checksum of ${cmd.checksum} firmwareId: ${cmd.firmwareId}, manufacturerId: ${cmd.firmwareId}")
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
    debug("---CONFIGURATION REPORT V2--- ${device.displayName} parameter ${cmd.parameterNumber} with a byte size of ${cmd.size} is set to ${cmd.configurationValue}")
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
    debug("---CONFIGURATION REPORT V1--- ${device.displayName} parameter ${cmd.parameterNumber} with a byte size of ${cmd.size} is set to ${cmd.configurationValue}")
    def map = [ name: "battery"]
    if (cmd.parameterNumber == 42) {
        if (cmd.configurationValue == [255]) {
            map.value = 0
            map.descriptionText = "${device.displayName} remote battery is low"
            debug("${device.displayName} remote battery is low")
        } else {
            map.value = 100
            map.descriptionText = "${device.displayName} remote battery is good"
            debug("${device.displayName} remote battery is good")
        }
        map.isStateChange = true
    }
    createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.associationv2.AssociationReport cmd) {
    cmd.nodeId.each({debug("AssociationReport: '${cmd}', hub: '$zwaveHubNodeId' reports nodeId: '$it' is associated in group: '${cmd.groupingIdentifier}'")})
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
    debug("Hail received: ${cmd}")
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
    if (cmd.value == 0) {
        off()
    } else {
        on()
    }
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
    debug(cmd)
    [name: "alarm", value: cmd.value ? "on" : "off", type: "physical", displayed: true, isStateChange: true]
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd)
{
    debug(cmd)
    [name: "alarm", value: cmd.value ? "on" : "off", type: "digital", displayed: true, isStateChange: true]
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinarySet cmd)
{
    debug(cmd)
    [name: "alarm", value: cmd.value ? "on" : "off", type: "digital", displayed: true, isStateChange: true]
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
    debug("Unhandled: $cmd sent to ${device.displayName}")
    createEvent(descriptionText: cmd.toString(), isStateChange: false)
}

def getbatt() {
    debug("Getting battery level for remote control on ${device.displayName}")
    def request = [
        zwave.configurationV1.configurationGet(parameterNumber: 42)
    ]
    commands(request)
}

def btest() {
    debug("Testing doorbell ring ${prefRingtone} on ${device.displayName}")
    on()
}

def atest() {
    debug("Testing alarm sound ${prefAlarmtone} on ${device.displayName}")
    both()
}

def playTrack(track) {
    disableNotifications()
    def request = [
        zwave.configurationV1.configurationSet(parameterNumber: 6, size: 1, scaledConfigurationValue: track.toInteger())
    ]
    commands(request)
}

def disableNotifications() {
    state.notification = false
}

def setRingtone(track) {
    def request = [
        zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: track)
    ]
    commands(request)
}

def strobe() {
    debug("Strobe command received")
    on()
}

def siren() {
    debug("Siren command received")
    on()
}

def both() {
    debug("Alarm test command received")
    def request = [
        zwave.configurationV1.configurationSet(parameterNumber: 6, size: 1, scaledConfigurationValue: prefAlarmtone.toInteger())
    ]
    commands(request)
}

def on() {
    if (state.notification) {
        sendEvent(name: "switch", value: "on", type: "physical", displayed: true, isStateChange: true)
        debug("${device.displayName} rung from button.")
    } else {
        debug("${device.displayName} rung from playTrack.")
    }
    def request = [
        zwave.basicV1.basicSet(value: 0xFF)
    ]
    commands(request)
}

def off() {
    debug("turning off ${device.displayName}")
    if (state.notification) {
        sendEvent(name: "switch", value: "off", type: "physical", displayed: true, isStateChange: true)
    }
    state.notification = true
    def request = [
        zwave.basicV1.basicSet(value: 0x00),
        zwave.basicV1.basicGet()
    ]
    commands(request)
}

def setLevel(level) {
    debug("setLevel request to ${device.displayName}")
    if (level < 0) {
        level = 0
    } else if (level > 100) {
        level = 100
    }
    def volume = (level / 10).toInteger()
    level = volume * 10
    debug("setting level to ${level} -> ${volume}")
    state.volume = volume
    if (state.notification) {
        sendEvent(name: "level", value: level, type: "physical", displayed: true, isStateChange: true)
    }
    def request = [
        zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: volume)
    ]
    commands(request)
}

def refresh() {
    debug("refresh request sent to ${device.displayName}")
    def request = [
        zwave.basicV1.basicGet(),
        zwave.switchBinaryV1.switchBinaryGet()
    ]
    state.notification = true
    commands(request)
}

def configure() {
    if (state.sec) {
        debug("secure configuration being sent to ${device.displayName}")
    } else {
        debug("configuration being sent to ${device.displayName}")
    }

    if (!state.volume) {
        state.volume = 10
    }
    debug("settings: ${settings.inspect()}, state: ${state.inspect()}")
    state.notification = true

    def request = [
        //associate with group 1 and remove any group 2 association
        zwave.associationV1.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId),
        zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId),
        zwave.associationV1.associationGet(groupingIdentifier:1),
        zwave.associationV1.associationGet(groupingIdentifier:2),

        // Get Version information
        zwave.versionV1.versionGet(),
        zwave.firmwareUpdateMdV2.firmwareMdGet(),

        // Enable to send notifications to associated devices (Group 1) (0=nothing, 1=hail CC, 2=basic CC report)
        zwave.configurationV1.configurationSet(parameterNumber: 80, size: 1, scaledConfigurationValue: 2),
        zwave.configurationV1.configurationGet(parameterNumber: 80),

        // send low battery notifications
        zwave.configurationV1.configurationSet(parameterNumber: 81, size: 1, scaledConfigurationValue: 1),
        zwave.configurationV1.configurationGet(parameterNumber: 81),

        // Set the repetitions for playing doorbell ringtone
        zwave.configurationV1.configurationSet(parameterNumber: 2, size: 1, scaledConfigurationValue: prefNumrings.toInteger()),
        zwave.configurationV1.configurationGet(parameterNumber: 2),

        // Set the default doorbell ringtone
        zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: prefRingtone.toInteger()),
        zwave.configurationV1.configurationGet(parameterNumber: 5),

        // Set the volume of ringtone
        zwave.configurationV1.configurationSet(parameterNumber: 8, size: 1, scaledConfigurationValue: state.volume),
        zwave.configurationV1.configurationGet(parameterNumber: 8),

        // define +- button function
        zwave.configurationV1.configurationSet(parameterNumber: 10, size: 1, scaledConfigurationValue: 0),
        zwave.configurationV1.configurationGet(parameterNumber: 10),
        zwave.configurationV1.configurationSet(parameterNumber: 11, size: 1, scaledConfigurationValue: 0),
        zwave.configurationV1.configurationGet(parameterNumber: 11),

        //  Get last known remote battery health
        zwave.configurationV1.configurationGet(parameterNumber: 42)
    ]
    commands(request)
}

private setConfigured() {
    updateDataValue("configured", "true")
}

private isConfigured() {
    getDataValue("configured") == "true"
}

// Helpers

def debug(msg) {
    if (state.debug) {
        log.debug(msg)
    }
}

private command(physicalgraph.zwave.Command cmd) {
    if (state.sec) {
        zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
    } else {
        cmd.format()
    }
}

private commands(commands, delay=100) {
    delayBetween(commands.collect{ command(it) }, delay)
}