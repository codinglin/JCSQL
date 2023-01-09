package cn.edu.gzhu.server;

import cn.edu.gzhu.backend.tbm.TableManager;
import cn.edu.gzhu.transport.Encoder;
import cn.edu.gzhu.transport.Package;
import cn.edu.gzhu.transport.Packager;
import cn.edu.gzhu.transport.Transporter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Server {
    private int port;
    TableManager tableManager;

    public Server(int port, TableManager tableManager) {
        this.port = port;
        this.tableManager = tableManager;
    }

    public void start() {
        ServerSocket ss = null;
        try {
            ss = new ServerSocket(port);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        System.out.println("Server listen to port: " + port);
        ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(10, 20, 1L, TimeUnit.SECONDS, new ArrayBlockingQueue<>(100), new ThreadPoolExecutor.CallerRunsPolicy());
        try {
            while (true) {
                Socket socket = ss.accept();
                Runnable worker = new HandleSocket(socket, tableManager);
                poolExecutor.execute(worker);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try{
                ss.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }
}

class HandleSocket implements Runnable {
    private Socket socket;
    private TableManager tableManager;

    public HandleSocket(Socket socket, TableManager tableManager) {
        this.socket = socket;
        this.tableManager = tableManager;
    }

    @Override
    public void run() {
        InetSocketAddress address = (InetSocketAddress) socket.getRemoteSocketAddress();
        System.out.println("Establish connection: " + address.getAddress().getHostAddress()+":"+address.getPort());
        Packager packager = null;
        try {
            Transporter transporter = new Transporter(socket);
            Encoder encoder = new Encoder();
            packager = new Packager(transporter, encoder);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                socket.close();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
            return;
        }
        Executor executor = new Executor(tableManager);
        while (true) {
            Package pkg = null;
            try {
                pkg = packager.receive();
            } catch (Exception e) {
                break;
            }
            byte[] sql = pkg.getData();
            byte[] result = null;
            Exception err = null;
            try {
                result = executor.execute(sql);
            } catch (Exception e) {
                err = e;
                err.printStackTrace();
            }
            pkg = new Package(result, err);
            try {
                packager.send(pkg);
            } catch (Exception e) {
                e.printStackTrace();
                break;
            }
        }
        executor.close();
        try {
            packager.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
