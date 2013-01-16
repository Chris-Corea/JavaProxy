import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ProxyServer
{
	private ServerSocket serverSocket;

	public ProxyServer(int port)
	{
		try
		{
			serverSocket = new ServerSocket(port, 15);
		} catch (IOException e)
		{
			e.printStackTrace();
			System.out.println("unable to start at port 8080");
			System.exit(0);
		}
	}
	
	public void start() throws IOException
	{
		Socket socket = null;
		while (true)
		{
			try
			{
				socket = serverSocket.accept();
				
				ClientHandler handler = new ClientHandler(this, socket);
				Thread clientThread = new Thread(handler);
				clientThread.start();
			} catch (IOException e)
			{
				System.err.println("error creating socket connection");
				System.exit(0);
			}
		}
	}
	
	public static void main(String[] args)
	{
		// need to start service to listen to incoming clients 
		ProxyServer ps = new ProxyServer(8080);
		try
		{
			ps.start();
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
