package com.fatwire.gst.jmx;

public class ClientTest {

	public static void main(String[] a) throws Exception {
		Client.main(new String[] { "localhost:51115", "*:*" });
		Client.main(new String[] { "localhost:51115", "java.lang:*" });
		Client.main(new String[] { "localhost:51115", "java.lang:type=Runtime",
				"*" });
		Client.main(new String[] { "localhost:51115", "java.lang:type=Runtime",
				"Attributes" });
		Client.main(new String[] { "localhost:51115", "java.lang:type=Runtime",
				"VmName" });
		Client.main(new String[] { "localhost:51115", "java.lang:type=Runtime",
				"VmName", "VmVendor" });
		Client.main(new String[] { "localhost:51115",
				"java.lang:name=Eden Space,type=MemoryPool", "*" });
		Client
				.main(new String[] { "localhost:51115",
						"java.lang:name=Eden Space,type=MemoryPool",
						"CollectionUsage" });

		Client.main(new String[] { "localhost:51115",
				"Catalina:type=Manager,*", "*" });
		Client.main(new String[] { "localhost:51115",
				"Catalina:type=Manager,*", "Attributes" });
		Client.main(new String[] { "localhost:51115", "Catalina:*",
				"activeSessions" });

	}

}
