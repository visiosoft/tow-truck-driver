package com.mpo.towtruckdriver

import com.rabbitmq.client.ConnectionFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RabbitMQService {
    companion object {
        private const val HOST = "172.20.96.1"
        private const val PORT = 5672
        private const val USERNAME = "saif"
        private const val PASSWORD = "saif"
        private const val VIRTUAL_HOST = "/"
        private const val QUEUE_NAME = "testQueue"
    }

    fun sendMessage(message: String) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val connectionFactory = ConnectionFactory().apply {
                    host = HOST
                    port = PORT
                    username = USERNAME
                    password = PASSWORD
                    virtualHost = VIRTUAL_HOST
                }

                connectionFactory.newConnection().use { connection ->
                    connection.createChannel().use { channel ->
                        // Declare the queue
                        channel.queueDeclare(QUEUE_NAME, false, false, false, null)
                        
                        // Send the message
                        channel.basicPublish("", QUEUE_NAME, null, message.toByteArray())
                        
                        withContext(Dispatchers.Main) {
                            println("Message sent successfully: $message")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    println("Error sending message: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }
} 