package dev.akkraizen.berill.api

interface MaintenanceSource : Capability { suspend fun update() }
