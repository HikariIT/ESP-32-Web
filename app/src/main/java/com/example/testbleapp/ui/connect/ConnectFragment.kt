package com.example.testbleapp.ui.connect

import android.Manifest
import android.app.Activity
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import com.example.testbleapp.databinding.FragmentConnectBinding
import java.util.*

class ConnectFragment : Fragment() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private lateinit var bluetoothGatt: BluetoothGatt

    private var deviceMap = mutableMapOf<String, String>()
    private var _binding: FragmentConnectBinding? = null
    private val binding get() = _binding!!

    private val bluetoothEnableRequest = 1
    private val serviceUUID = UUID.fromString("000000FF-0000-1000-8000-00805F9B34FB")
    private val characteristicUUID = UUID.fromString("0000FF03-0000-1000-8000-00805F9B34FB")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("GattCallback", "Successfully connected to GATT server")
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("GattCallback", "Successfully disconnected from GATT server")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Service discovery successful, proceed to get the characteristic
                val characteristic = gatt?.getService(serviceUUID)?.getCharacteristic(characteristicUUID)
                gatt?.readCharacteristic(characteristic)
            } else {
                // Service discovery failed
                Log.i("GattCallback", "Service discovery failed")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicRead(gatt, characteristic, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Toast on UI thread
                val stringCharacteristic = characteristic?.getStringValue(0);
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Characteristic read successfully: $stringCharacteristic",
                        Toast.LENGTH_SHORT).show()
                }
                Log.i("GattCallback", "Read characteristic value: $stringCharacteristic")
            } else {
                // Characteristic read unsuccessful
                Log.i("GattCallback", "Characteristic read unsuccessful")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == bluetoothEnableRequest) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // All permissions granted, proceed with your app logic
                // Log the permissions granted
                Log.i("Permissions", "Granted")
            } else {
                // At least one permission was denied
                // Show message
                Toast.makeText(requireContext(), "Permissions Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectBinding.inflate(inflater, container, false)
        val view = binding.root

        requestBluetoothPermissions();

        val bluetoothManager =
            requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        deviceListAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1)
        binding.deviceListView.adapter = deviceListAdapter

        binding.deviceListView.setOnItemClickListener { _, _, position, _ ->
            val deviceName = deviceListAdapter.getItem(position)
            // Toast.makeText(requireContext(), "Selected Device: $deviceName", Toast.LENGTH_SHORT).show()
            val device = bluetoothAdapter.getRemoteDevice(deviceMap[deviceName])
            bluetoothGatt = device.connectGatt(requireContext(), false, gattCallback)
            bluetoothGatt.disconnect()
        }

        scanForDevices()

        return view
    }

    private fun scanForDevices() {
        if (bluetoothAdapter.isEnabled) {
            val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
            bluetoothLeScanner.startScan(object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    super.onScanResult(callbackType, result)
                    result?.let {
                        val device = it.device
                        val deviceName = device.name ?: "Unnamed Device"
                        // Check if device is already in list

                        if (deviceListAdapter.getPosition(deviceName) < 0) {
                            deviceListAdapter.add(deviceName)
                            deviceMap[deviceName] = device.address
                            deviceListAdapter.notifyDataSetChanged()
                        }
                    }
                }
            })
        } else {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, bluetoothEnableRequest)
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            requestPermissions(permissions, bluetoothEnableRequest)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == bluetoothEnableRequest) {
            if (resultCode == Activity.RESULT_OK) {
                scanForDevices()
            } else {
                Toast.makeText(requireContext(), "Bluetooth is required for this feature.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
    }
}
