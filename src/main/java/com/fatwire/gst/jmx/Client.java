package com.fatwire.gst.jmx;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

/**
 * A Simple Command-Line JMX Client. Tested against the JDK 1.6.0 JMX Agent. See
 * <a href="http://java.sun.com/j2se/1.5.0/docs/guide/management/agent.html">
 * Monitoring and Management Using JMX</a>.
 * <p>
 * Can supply credentials and do primitive string representation of tabular and
 * composite openmbeans.
 * 
 * @author stack
 * @author Dolf Dijkstra
 */
public class Client {

	/**
	 * Usage string.
	 */
	private static final String USAGE = "Usage: java -jar"
			+ " gst-jmx-client.jar USER:PASS@HOST:PORT [BEAN] [COMMAND]\n"
			+ "Options:\n"
			+ " USER:PASS Username and password. Not required\n"
			+ "           E.g. 'controlRole:secret'\n"
			+ " HOST:PORT Hostname and port to connect to. Required.\n"
			+ "           E.g. localhost:8081.\n"
			+ " BEAN      Optional target bean name. This can be a wildcard name.\n"
			+ " COMMAND   Optional attribute to fetch. Can be repeated for mulitple attributes\n"
			+ "           If '*' supplied, all attributes are listed.\n"
			+ "           Pass 'Attributes' to get listing of all attributes and \n"
			+ "           their values.\n"
			+ "Requirements:\n"
			+ " JDK1.5.0. If connecting to a SUN 1.5.0 JDK JMX Agent, remote side"
			+ " must be\n"
			+ " started with system properties such as the following:\n"
			+ "     -Dcom.sun.management.jmxremote.port=PORT\n"
			+ "     -Dcom.sun.management.jmxremote.authenticate=false\n"
			+ "     -Dcom.sun.management.jmxremote.ssl=false\n"
			+ " The above will start the remote server with no password. See\n"
			+ " http://java.sun.com/j2se/1.5.0/docs/guide/management/agent.html"
			+ " for more on 'Monitoring and Management via JMX'.\n\n"
			+ " Client Use Examples:\n"
			+ "           To list al bean names for all domains\n"
			+ "           % java -jar cmdline-jmxclient-X.X.jar localhost:51115 '*:*'\n\n"
			+ "           To list al bean names for the java.lang domain, login with 'controlRole:secret'\n"
			+ "           % java -jar cmdline-jmxclient-X.X.jar controlRole:secret@localhost:51115 'java.lang:*'\n\n"
			+ "           To list al attribute names for 'java.lang:type=Runtime'\n"
			+ "           % java -jar cmdline-jmxclient-X.X.jar localhost:51115 java.lang:type=Runtime '*'\n\n"
			+ "           To list al attribute values for 'java.lang:type=Runtime'\n"
			+ "           % java -jar cmdline-jmxclient-X.X.jar localhost:51115 java.lang:type=Runtime Attributes\n\n"
			+ "           To list attribute 'VmName' for 'java.lang:type=Runtime'\n"
			+ "           % java -jar cmdline-jmxclient-X.X.jar localhost:51115 java.lang:type=Runtime VmName\n\n"
			+ "           To list attribute 'VmName' and 'VmVendor' for 'java.lang:type=Runtime'\n"
			+ "           % java -jar cmdline-jmxclient-X.X.jar localhost:51115 java.lang:type=Runtime VmName VmVendor\n\n"
	        + "Output:    each line contains the bean name, the attribute name and the attribute value, tab seperated.\n";


	public static void main(String[] args) throws Exception {
		Client client = new Client();
		long t = System.nanoTime();
		client.execute(args);
		long t2 = System.nanoTime();
		System.out.println(Long.toString((t2-t)/1000));
	}

	protected static void usage() {
		usage(0, null);
	}

	protected static void usage(int exitCode, String message) {
		if (message != null && message.length() > 0) {
			System.out.println(message);
		}
		System.out.println(USAGE);
		System.exit(exitCode);
	}

	/**
	 * Constructor.
	 */
	public Client() {
		super();
	}

	/**
	 * Parse a 'login:password' string. Assumption is that no colon in the login
	 * name.
	 * 
	 * @param userpass
	 * @return Array of strings with login in first position.
	 */
	protected String[] parseUserpass(final String userpass) {
		if (userpass == null || userpass.equals("-")) {
			return new String[] { null, null };
		}
		int index = userpass.indexOf(':');
		if (index <= 0) {
			throw new RuntimeException("Unable to parse: " + userpass);
		}
		return new String[] { userpass.substring(0, index),
				userpass.substring(index + 1) };
	}

	/**
	 * @param login
	 * @param password
	 * @return Credentials as map for RMI.
	 */
	protected Map<String, ?> formatCredentials(final String login,
			final String password) {
		Map<String, String[]> env = null;
		String[] creds = new String[] { login, password };
		env = new HashMap<String, String[]>(1);
		env.put(JMXConnector.CREDENTIALS, creds);
		return env;
	}

	protected JMXConnector getJMXConnector(final String hostport,
			final String login, final String password) throws IOException {
		// Make up the jmx rmi URL and get a connector.
		JMXServiceURL rmiurl = new JMXServiceURL("service:jmx:rmi://"
				+ hostport + "/jndi/rmi://" + hostport + "/jmxrmi");
		return JMXConnectorFactory.connect(rmiurl, formatCredentials(login,
				password));
	}

	protected ObjectName getObjectName(final String beanname)
			throws MalformedObjectNameException, NullPointerException {
		return notEmpty(beanname) ? new ObjectName(beanname) : null;
	}

	/**
	 * Version of execute called from the cmdline. Prints out result of
	 * execution on stdout. Parses cmdline args. Then calls
	 * {@link #execute(String, String, String, String, String[])}.
	 * 
	 * @param args
	 *            Cmdline args.
	 * @throws Exception
	 */
	protected void execute(final String[] args) throws Exception {
		// Process command-line.
		if (args.length == 0 || args.length == 1) {
			usage();
		}
		// user:pass@host:port
		String[] x = args[0].split("@");

		String userpass = x.length == 1 ? null : x[0];
		String hostport = x.length == 1 ? x[0] : x[1];
		String beanname = null;
		String[] command = null;
		if (args.length > 1) {
			beanname = args[1];
		}
		if (args.length > 2) {
			command = new String[args.length - 2];
			for (int i = 2; i < args.length; i++) {
				command[i - 2] = args[i];
			}
		}
		String[] loginPassword = parseUserpass(userpass);
		Object[] result = execute(hostport, loginPassword[0], loginPassword[1],
				beanname, command);
		// Print out results on stdout. Only log if a result.
		if (result != null) {
			for (int i = 0; i < result.length; i++) {
				if (result[i] != null && result[i].toString().length() > 0) {
					System.out.println(result[i].toString());
				}
			}
		}
	}

	/**
	 * Execute command against remote JMX agent.
	 * 
	 * @param hostport
	 *            'host:port' combination.
	 * @param login
	 *            RMI login to use.
	 * @param password
	 *            RMI password to use.
	 * @param beanname
	 *            Name of remote bean to run command against.
	 * @param command
	 *            Array of commands to run.
	 * @return Array of results -- one per command.
	 * @throws Exception
	 */
	protected Object[] execute(final String hostport, final String login,
			final String password, final String beanname, final String[] command)
			throws Exception {
		JMXConnector jmxc = getJMXConnector(hostport, login, password);
		Object[] result = null;
		try {
			result = readBeans(jmxc.getMBeanServerConnection(),
					getObjectName(beanname), command);
		} finally {
			jmxc.close();
		}
		return result;
	}

	protected boolean notEmpty(String s) {
		return s != null && s.length() > 0;
	}

	protected String[] readBeans(final MBeanServerConnection mbsc,
			final ObjectName objName, final String[] command) throws Exception {

		Set<ObjectInstance> beans = mbsc.queryMBeans(objName, null);
		if (beans.size() == 0) {
			throw new RuntimeException(objName.getCanonicalName()
					+ " not registered.");
		}
		List<String> list = new ArrayList<String>();

		for (ObjectInstance obj : beans) {
			if (obj != null) {

				if (command == null) {
					// If no command, then print object names.
					list.add(obj.getObjectName().getCanonicalName());
				} else if (command.length == 1 && "*".equals(command[0])) {
					// If command = *, then print out list of attributes.
					list.add(listOptions(mbsc, obj));
				} else {
					String[] names;
					MBeanAttributeInfo[] attributeInfo = mbsc.getMBeanInfo(
							obj.getObjectName()).getAttributes();

					names = filterAttributes(command, attributeInfo);
					AttributeList l = mbsc.getAttributes(obj.getObjectName(),
							names);
					for (Attribute attr : l.asList()) {
						list.add(obj.getObjectName() + "\t" + attr.getName()
								+ "\t" + toString(attr.getValue()));
					}

				}

			} else {
				throw new RuntimeException("Unexpected object type: " + obj);
			}

		}
		return list.toArray(new String[list.size()]);

	}

	/**
	 * @param command
	 * @param attributeInfo
	 * @return
	 */
	private String[] filterAttributes(final String[] command,
			MBeanAttributeInfo[] attributeInfo) {

		boolean all = command.length == 1 && "Attributes".equals(command[0]);

		List<String> names = new LinkedList<String>();

		for (int j = 0; j < attributeInfo.length; j++) {
			for (int i = 0; i < command.length; i++) {
				if (all || command[i].equals(attributeInfo[j].getName())) {
					names.add(attributeInfo[j].getName());
				}
			}
		}

		return names.toArray(new String[names.size()]);
	}

	/**
	 * @param result
	 * @return
	 */
	private String toString(Object result) {
		if (result == null)
			return null;
		// Look at the result. Is it of composite or tabular type?
		// If so, convert to a String representation.
		StringBuilder b;
		if (result instanceof CompositeData) {
			b = recurseCompositeData(new StringBuilder("\n"), "", "",
					(CompositeData) result);
		} else if (result instanceof TabularData) {
			b = recurseTabularData(new StringBuilder("\n"), "", "",
					(TabularData) result);
		} else if (result instanceof String[]) {
			String[] strs = (String[]) result;
			StringBuilder buffer = new StringBuilder("\n");
			for (int i = 0; i < strs.length; i++) {
				buffer.append(strs[i]);
				buffer.append("\n");
			}
			b = buffer;
		} else if (result instanceof AttributeList) {
			AttributeList list = (AttributeList) result;
			if (list.size() <= 0) {
				b = null;
			} else {
				StringBuilder buffer = new StringBuilder("\n");
				for (Attribute a: list.asList()) {
					buffer.append(a.getName());
					buffer.append(": ");
					buffer.append(a.getValue());
					buffer.append("\n");
				}
				b = buffer;
			}
		} else {
			b = new StringBuilder(String.valueOf(result));
		}
		return b.toString();
	}


	protected StringBuilder recurseTabularData(StringBuilder buffer,
			String indent, String name, TabularData data) {
		addNameToBuffer(buffer, indent, name);
		java.util.Collection<?> c = data.values();
		for (Iterator<?> i = c.iterator(); i.hasNext();) {
			Object obj = i.next();
			if (obj instanceof CompositeData) {
				recurseCompositeData(buffer, indent + " ", "",
						(CompositeData) obj);
			} else if (obj instanceof TabularData) {
				recurseTabularData(buffer, indent, "", (TabularData) obj);
			} else {
				buffer.append(obj);
			}
		}
		return buffer;
	}

	protected StringBuilder recurseCompositeData(StringBuilder buffer,
			String indent, String name, CompositeData data) {
		indent = addNameToBuffer(buffer, indent, name);
		for (Iterator<String> i = data.getCompositeType().keySet().iterator(); i
				.hasNext();) {
			String key = i.next();
			Object o = data.get(key);
			if (o instanceof CompositeData) {
				recurseCompositeData(buffer, indent + " ", key,
						(CompositeData) o);
			} else if (o instanceof TabularData) {
				recurseTabularData(buffer, indent, key, (TabularData) o);
			} else {
				buffer.append(indent);
				buffer.append(key);
				buffer.append(": ");
				buffer.append(o);
				buffer.append("\n");
			}
		}
		return buffer;
	}

	protected String addNameToBuffer(StringBuilder buffer, String indent,
			String name) {
		if (name == null || name.length() == 0) {
			return indent;
		}
		buffer.append(indent);
		buffer.append(name);
		buffer.append(":\n");
		// Move all that comes under this 'name' over by one space.
		return indent + " ";
	}



	protected String listOptions(MBeanServerConnection mbsc,
			ObjectInstance instance) throws InstanceNotFoundException,
			IntrospectionException, ReflectionException, IOException {
		StringBuilder result = new StringBuilder();
		result.append(instance.getObjectName());

		// result.append(" ("+instance.getClassName() +")");
		result.append("\n");
		MBeanInfo info = mbsc.getMBeanInfo(instance.getObjectName());
		MBeanAttributeInfo[] attributes = info.getAttributes();
		if (attributes.length > 0) {
			result.append("Attributes:");
			result.append("\n");
			for (int i = 0; i < attributes.length; i++) {
				result.append(' ' + attributes[i].getName() + ": "
						+ attributes[i].getDescription() + " (type="
						+ attributes[i].getType() + ")");
				result.append("\n");
			}
		}

		return result.toString();
	}

}
