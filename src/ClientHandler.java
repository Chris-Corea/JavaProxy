import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClientHandler implements Runnable
{
	protected DataInputStream client_in;
	protected DataOutputStream client_out;
	protected Socket client_socket;
	protected DataInputStream remote_in;
	protected DataOutputStream remote_out;
	protected Socket remote_socket;
	protected ProxyServer proxyServer;

	private boolean commIsPost;

	private StringBuffer header;
	private HashMap<String, String> headerMap;
	private String dest;
	private byte[] postBody;

	public ClientHandler(ProxyServer proxyServer, Socket socket)
	{
		try
		{
			this.proxyServer = proxyServer;
			this.client_socket = socket;
			this.client_in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			this.client_out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
			this.headerMap = new HashMap<String, String>();
			this.header = new StringBuffer();
		} catch (IOException e)
		{
			System.err.println(e.getMessage());
		}
	}

	public void run()
	{
		try
		{
			String line = "";
			int contentLength = 0;
			int count = 0;
			while ((line = readLineFromInput(client_in)) != null)
			{
				if (count == 0)
					extractFirstLine(line);
				else if (line.trim().equals(""))
				{ // done with headers, if post get more stuff
					if (commIsPost)
						if (headerMap.containsKey("content-length:"))
						{
							String res = headerMap.get("content-length:");
							contentLength = Integer.valueOf(res);
						}
					break;
				} else
					getHeaderLine(line);
				count++;
			}

			if (commIsPost)
			{
				byte[] buffer = new byte[contentLength];
				client_in.read(buffer, 0, contentLength);
				postBody = buffer;
			}
			/* done reading from client */
			/* write to server */
			writeToServer(remote_out);
			/* done writing to server */
			/* read from server */
			byte[] buffer = new byte[1024];
			int numRead = 0;
			while ((numRead = remote_in.read(buffer, 0, buffer.length)) > 0)
				client_out.write(buffer, 0, numRead);
			client_out.flush();
      /* done reading from server */
			remote_socket.close();
			client_socket.close();
		} catch (IOException e)
		{
			System.err.println(e.getMessage());
		}
	}

	private void getHeaderLine(String line)
	{
		String[] headerLine = line.split(" ", 2);

		if (headerLine[0].equalsIgnoreCase("user-agent:")
				|| headerLine[0].equalsIgnoreCase("referer:")
				|| headerLine[0].equalsIgnoreCase("proxy-connection:"))
			return;

		if (headerLine[0].toLowerCase().equals("host:"))
			connectToRemote(headerLine[1].toLowerCase().trim());

		headerMap.put(normalizeHeader(headerLine[0]), headerLine[1]);
	}

	private String readLineFromInput(DataInputStream in)
	{
		// keeping track of previous char to know when to stop reading
		int currChar = 0;
		int prevChar = 0;
		int newLine = '\n';
		int ret = '\r';
		StringBuffer currHeader = new StringBuffer();
		try
		{
			while ((currChar = in.read()) != -1)
			{
				if (prevChar == ret && currChar == newLine)
					return currHeader.toString();
				else
				{
					prevChar = currChar;
					if (currChar != ret)
						currHeader.append((char) currChar);
				}
			}
		} catch (IOException e)
		{
			System.out.println("could not read from input stream");
		}
		return null;
	}

	private boolean extractFirstLine(String s)
	{
		String[] commLine = s.split(" ");
		String command = commLine[0];
		isPost(command);
		String uri = getPathFromUri(commLine[1]);
		String httpVersion = protocolVersion(commLine[2]);
		header.append(command + " " + uri + " " + httpVersion);
		return true;
	}

	private void connectToRemote(String path)
	{
		try
		{
			remote_socket = new Socket(path, 80);
			remote_out = new DataOutputStream(new BufferedOutputStream(remote_socket.getOutputStream()));
			remote_in = new DataInputStream(new BufferedInputStream(remote_socket.getInputStream()));
		} catch (UnknownHostException e)
		{
			System.out.println("could not connect to server: " + dest);
		} catch (IOException e)
		{
			System.out.println("could not connect to server **" + dest);
		}
	}

	private void writeToServer(DataOutputStream out) throws IOException
	{
		out.write((header.toString() + "\n").getBytes());
		int contentLength = 0;
		Iterator<String> iter = headerMap.keySet().iterator();
		while (iter.hasNext())
		{
			String temp = iter.next();
			if (temp.equals("content-length:"))
				contentLength = Integer.valueOf(headerMap.get(temp).trim());
			out.write((temp + " " + headerMap.get(temp) + "\n").getBytes());
		}
		out.write(("\r" + "\n").getBytes());
		if (contentLength > 0)
			out.write(postBody);
		out.flush();
	}

	/**
	 * 
	 * @param header
	 * @return equivalent to header.toLowerCase()
	 */
	private String normalizeHeader(String header)
	{
		return header.toLowerCase();
	}

	private void isPost(String command)
	{
		commIsPost = (command.toLowerCase().equals("post"));
	}

	private String protocolVersion(String version)
	{
		return "HTTP/1.0";
	}

	private String getPathFromUri(String path)
	{
		Pattern p = Pattern.compile("(http://)?([^/]+)?(/(.*))?");
		Matcher m = p.matcher(path.toLowerCase().trim());
		if (m.matches())
			return "/" + m.group(4);
		return path;
	}
}
