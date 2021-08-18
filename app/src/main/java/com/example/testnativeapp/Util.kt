package com.example.testnativeapp

import android.hardware.usb.UsbDevice

/*
* @author Tkachov Vasyl
* @since 23.07.2021
*/
object Util {

    fun readUsbData(device: UsbDevice) {
        var returnValue = ""
        returnValue += "Name: " + device.deviceName
        returnValue += """
            
            ID: ${device.deviceId}
            """.trimIndent()
        returnValue += """
            
            Protocol: ${device.deviceProtocol}
            """.trimIndent()
        returnValue += """
            
            Class: ${device.deviceClass}
            """.trimIndent()
        returnValue += """
            
            Subclass: ${device.deviceSubclass}
            """.trimIndent()
        returnValue += """
            
            Product ID: ${device.productId}
            """.trimIndent()
        returnValue += """
            
            Vendor ID: ${device.vendorId}
            """.trimIndent()
        returnValue += """
            
            Interface count: ${device.interfaceCount}
            """.trimIndent()

        for (i in 0 until device.interfaceCount) {
            var usbInterface = device.getInterface(i)
            returnValue += "\n  Interface $i"
            returnValue += """
	Interface ID: ${usbInterface.getId()}"""
            returnValue += """
	Class: ${usbInterface.getInterfaceClass()}"""
            returnValue += """
	Protocol: ${usbInterface.getInterfaceProtocol()}"""
            returnValue += """
	Subclass: ${usbInterface.getInterfaceSubclass()}"""
            returnValue += """
	Endpoint count: ${usbInterface.getEndpointCount()}"""
            for (j in 0 until usbInterface.getEndpointCount()) {
                returnValue += "\n\t  Endpoint $j"
                returnValue += """
		Address: ${usbInterface.getEndpoint(j).getAddress()}"""
                returnValue += """
		Attributes: ${usbInterface.getEndpoint(j).getAttributes()}"""
                returnValue += """
		Direction: ${usbInterface.getEndpoint(j).getDirection()}"""
                returnValue += """
		Number: ${usbInterface.getEndpoint(j).getEndpointNumber()}"""
                returnValue += """
		Interval: ${usbInterface.getEndpoint(j).getInterval()}"""
                returnValue += """
		Type: ${usbInterface.getEndpoint(j).getType()}"""
                returnValue += """
		Max packet size: ${usbInterface.getEndpoint(j).getMaxPacketSize()}"""
            }
        }
        System.out.println(returnValue)
    }
}