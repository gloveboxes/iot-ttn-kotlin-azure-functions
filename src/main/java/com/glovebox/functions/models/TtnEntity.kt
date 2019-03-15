package com.glovebox.functions.models

import com.microsoft.azure.storage.table.TableServiceEntity

// https://stackoverflow.com/questions/44320495/how-do-i-read-an-environment-variable-in-kotlin
// https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java
// https://github.com/Azure/azure-functions-java-library
// https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java
// https://github.com/Azure-Samples/storage-table-java-getting-started/blob/master/src/main/java/com/microsoft/azure/cosmosdb/tablesample/TableBasics.java
// https://github.com/Azure-Samples/storage-table-java-getting-started/tree/master/src/main/java/com/microsoft/azure/cosmosdb/tablesample
// https://mvnrepository.com/artifact/com.microsoft.azure/azure-storage
// https://docs.microsoft.com/en-us/java/api/com.microsoft.azure.storage.table._cloud_table.cloudtable?view=azure-java-legacy&viewFallbackFrom=azure-java-stable#com_microsoft_azure_storage_table__cloud_table_CloudTable_final_StorageUri_
//https://www.programcreek.com/java-api-examples/?api=com.microsoft.azure.storage.table.TableOperation
// https://blogs.msdn.microsoft.com/windowsazurestorage/2012/03/05/windows-azure-storage-client-for-java-tables-deep-dive/

// The Things Network (TTN) Entity

class TtnEntity {
    val app_id: String? = null
    val dev_id: String? = null
    val counter: Int = 0
    val payload_raw: String? = null
    val payload_fields: Map<String, Double>? = null
}