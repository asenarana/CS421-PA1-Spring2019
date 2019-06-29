/*
Asena Rana Yozgatli
21000132

CS421 - Computer Notworks
Programming Assignment 1: SeekAndDestroy
Spring 2019
 */

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SeekAndDestroy {

    private static final int DATA_PORT = 20000;
    private static final Charset ENCODING = StandardCharsets.US_ASCII;
    private static final String POSTFIX = "\r\n";
    private static final String USERNAME = "bilkent";
    private static final String PASSWORD = "cs421";
    private static final String FILE_NAME = "received.jpg";
    private static final String TARGET_NAME = "target.jpg";
    private static final String EMPTY = "";

    private static ServerSocket dataSocket;
    private static Socket controlSocket;
    private static DataOutputStream controlOut;
    private static DataInputStream controlIn;

    private static byte[] file;
    private static String data;
    private static boolean pre_data = true;

    public static void main(String[] args)
    {
        InetAddress address= strToINet(args[1]);
        int controlPort = Integer.parseInt(args[2]);
        initConn(address, controlPort);
        seekAndDestroy();
        quit(0);
    }

    private static void seekAndDestroy()
    {
        if(!seek())
        {
            System.out.println("Target file not found!");
            quit(5);
        }
        command("RETR", TARGET_NAME, true);
        readData(false);
        saveFile();
        command("DELE", TARGET_NAME, true);
    }

    private static boolean seek()
    {
        List<String> dirs = new ArrayList<>();

        command("NLST", true);
        readData(true);
        if( data == null) return false;
        for (String content: data.split(POSTFIX))
        {
            if((content.split(":")[1]).equals("d"))
            {
                dirs.add(content.split(":")[0]);
            }
            else if((content.split(":")[1]).equals("f"))
            {
                if((content.split(":")[0]).equals(TARGET_NAME)) return true;
            }
            else
            {
                System.out.println("Invalid type returned from server: " + content.split(":")[1] + "\n");
                quit(5);
            }
        }
        if(dirs.isEmpty()) return false;
        for(String dir: dirs)
        {
            command("CWD", dir, true);
            if(seek()) return true;
            command("CDUP", true);
        }
        return false;
    }

    private static void readData(boolean mode)
    {
        // mode true = directory info
        // mode false = retrieve file
        Socket dataListener;
        BufferedInputStream dataBuf;
        byte[] headerByte = new byte[2];
        byte[] dataByte;
        short dataLength;

        try
        {
            dataListener = dataSocket.accept();
            dataBuf = new BufferedInputStream(dataListener.getInputStream());
            dataBuf.mark(2);
            dataBuf.read(headerByte, 0, 2);
            dataLength = ByteBuffer.wrap(headerByte).getShort();
            dataByte = new byte[dataLength + 2];
            dataBuf.reset();
            dataBuf.read(dataByte, 0,dataLength+2);
            if(mode)
            {
                if(dataLength == 0)
                {
                    data = null;
                }
                else
                {
                    data = new String(dataByte, 2, dataLength, ENCODING);
                }
            }
            else
            {
                if(dataLength == 0)
                {
                    System.out.println("Server returned empty file.\n");
                    file = new byte[0];
                }
                else
                {
                    file = Arrays.copyOfRange(dataByte, 2, dataLength+2);
                }
            }
            dataListener.close();
        }
        catch (IOException e)
        {
            System.out.println("Error while receiving message from data connection.\n" + e);
            quit(5);
        }
    }

    private static void saveFile()
    {
        try
        {
            Files.write(Paths.get(FILE_NAME), file, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException e)
        {
            System.out.println("Error while saving the target file.\n" + e);
            quit(5);
        }
    }

    private static boolean command(String commandName, boolean control)
    {
        return command(commandName, EMPTY, control);
    }

    private static boolean command(String commandName, String argument, boolean control)
    {
        String command;
        if((command = constructCommand(commandName, argument, control)) == null)
        {
            if(control) quit(5);
            return false;
        }
        return sendCommand(command, control);
    }

    private static String constructCommand(String commandName, String argument, boolean control)
    {
        String command;
        String space = " ";
        boolean type;

        switch (commandName)
        {
            case "USER":
                type = true;
                break;
            case "PASS":
                type = true;
                break;
            case "PORT":
                type = true;
                break;
            case "NLST":
                type = false;
                break;
            case "CWD":
                type = true;
                break;
            case "CDUP":
                type = false;
                break;
            case "RETR":
                type = true;
                break;
            case "DELE":
                type = true;
                break;
            case "QUIT":
                type = false;
                break;
            default:
                System.out.println("Invalid command name: " + commandName + "\n");
                if(control) quit(5);
                return null;
        }
        if (type)
        {
            if(argument.equals(EMPTY))
            {
                System.out.println("Argument is needed for command " + commandName + ".\n");
                if(control) quit(5);
                return null;
            }
            command = ((commandName.concat(space)).concat(argument)).concat(POSTFIX);
        }
        else
        {
            command = commandName.concat(POSTFIX);
        }
        return command;
    }

    private static boolean sendCommand(String command, boolean control)
    {
        byte[] commandByte = command.getBytes(ENCODING);
        try
        {
            controlOut.write(commandByte);
        }
        catch (IOException e)
        {
            System.out.println("Error while sending command to server.\n" + e);
            if(control) quit(5);
            return false;
        }
        return checkResponse(control);
    }

    private static boolean checkResponse(boolean control)
    {
        int resultSize;
        byte[] result = new byte[265];
        String response;
        String message;
        try
        {
            resultSize = controlIn.read(result);
            if(resultSize == -1)
            {
                System.out.println("Server returned invalid results.");
                if(control) quit(5);
                return false;
            }
            response = new String(result, ENCODING);
            if(response.startsWith("200")) return true;
            else if(response.startsWith("400"))
            {
                message = (response.split("400")[1]).split(POSTFIX)[0];
                System.out.println("Communication failure:\n" + message + "\n");
                if(control) quit(6);
                return false;
            }
            else
            {
                System.out.println("Server returned invalid response:\n" + response + "\n");
                if(control) quit(5);
                return false;
            }
        }
        catch (IOException e)
        {
            System.out.println("Error while receiving message from control connection.\n" + e);
            if(control) quit(5);
            return false;
        }
    }

    private static void initConn(InetAddress ip, int port)
    {
        initControlConn(ip, port);
        command("USER", USERNAME, true);
        command("PASS", PASSWORD, true);
        initDataConn(ip);
        command("PORT", Integer.toString(DATA_PORT), true);
    }

    private static void initControlConn(InetAddress ip, int port)
    {
        try
        {
            controlSocket = new Socket(ip, port);
        }
        catch (IOException e)
        {
            System.out.println("Error while setting up the control connection.\n" + e);
            quit(1);
        }
        try
        {
            controlIn = new DataInputStream(controlSocket.getInputStream());
            controlOut = new DataOutputStream(controlSocket.getOutputStream());
        }
        catch (IOException e)
        {
            System.out.println("Error while setting up the control connection.\n" + e);
            quit(2);
        }
    }

    private static void initDataConn(InetAddress ip)
    {
        try
        {
            dataSocket = new ServerSocket(DATA_PORT, 2, ip);
        }
        catch (IOException e)
        {
            System.out.println("Error while setting up the data connection.\n" + e);
            quit(3);
        }
        pre_data = false;
    }

    private static InetAddress strToINet(String address)
    {
        InetAddress IP;
        try {
            IP = InetAddress.getByName(address);
        }
        catch(UnknownHostException e)
        {
            IP = null;
            System.out.println("Error while resolving the internet address:\n" + e);
            quit(1);
        }
        return IP;
    }

    private static boolean closeControlConn()
    {
        try
        {
            controlSocket.close();
        }
        catch (IOException e)
        {
            System.out.println("Error while closing control connection.\n" + e);
            return false;
        }
        return true;
    }

    private static boolean closeDataConn()
    {
        try
        {
            dataSocket.close();
        }
        catch (IOException e)
        {
            System.out.println("Error while closing data connection.\n" + e);
            return false;
        }
        return true;
    }

    private static void quit( int code)
    {
        switch (code)
        {
            case 0:
                int exit_code = 0;
                if(!command("QUIT", false)) exit_code = 1;
                if(!closeControlConn()) exit_code = 1;
                if(!closeDataConn()) exit_code = 1;
                System.exit(exit_code);
                break;
            case 1:
                System.exit(1);
                break;
            case 2:
                closeControlConn();
                System.exit(1);
                break;
            case 3:
                command("QUIT", false);
                closeControlConn();
                System.exit(1);
                break;
            case 4:
                command("QUIT", false);
                closeControlConn();
                closeDataConn();
                System.exit(1);
                break;
            case 5:
                command("QUIT", false);
                closeControlConn();
                if(!pre_data)
                {
                    closeDataConn();
                }
                System.exit(1);
                break;
            case 6:
                closeControlConn();
                closeDataConn();
                System.exit(1);
                break;
            default:
                System.exit(1);
                break;
        }
    }
}
