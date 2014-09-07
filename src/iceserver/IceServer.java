package iceserver;

import api.CreatePDF;
import api.SendEmail;
import com.itextpdf.text.DocumentException;
import ice.Strings;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author alex
 */
public class IceServer
{
    /**
     * @param args the command line arguments
     * args[0] - PORT (config file PORT.txt)
     */
    static public String accpath;
    static public String logpath;
    static public String version;
    static public String toreg;
    static public Strings StringsConfigBM;
    
    static public void SetProp(String a,String t,String l,String v,String scf) throws IOException
    {
        accpath=a;
        logpath=l;
        version=v;
        toreg=t;
        StringsConfigBM = new Strings(scf);
    }
    public static void main(String[] args) throws IOException, DocumentException
    {
        if(args.length!=1)
        {
            System.out.println("args[0] - PORT (PORT.txt)");
            return;
        }
        List<String> al = GetArgsList(args[0]);
            SetProp(al.get(0), al.get(1), al.get(2), al.get(3), al.get(4));
            CreatePDF.SetProp(al.get(5));
            SendEmail.SetProp(al.get(7).split(" ")[0], al.get(7).split(" ")[1], al.get(6).split(" "));
        PrintStream st = new PrintStream(new FileOutputStream(args[3] + "DEBUG.txt",true));
        System.setErr(st);
        System.setOut(st);
        ServerSocket s = new ServerSocket(Integer.parseInt(args[0]));
        System.out.println(new Date().toString() +" Server Started");
        try
        {
            while (true)
            {
                // Блокируется до возникновения нового соединения:
                Socket socket = s.accept();
                try
                {
                    System.out.println(new Date().toString() + " Goto ServeOneJabber. InetAddress " + s.getInetAddress());
                    new ServeOneJabber(socket);
                }
                catch (IOException e)
                {
                    // Если завершится неудачей, закрывается сокет, в противном случае, нить закроет его:
                    socket.close();
                }
            }
        }
        finally
        {
            s.close();
        }
    }
    
    static private List<String> GetArgsList(String args)
    {
        List<String> al = null;
        try
        {
            BufferedReader br = new BufferedReader(new FileReader(args));
            String str;
            al = new ArrayList<String>();
            while ((str = br.readLine()) != null)
            {
                al.add(str);
            }
        }
        catch (FileNotFoundException ex)
        {
            Logger.getLogger(ServeOneJabber.class.getName()).log(Level.SEVERE, null, ex);
        }
        catch (IOException ex)
        {
            Logger.getLogger(ServeOneJabber.class.getName()).log(Level.SEVERE, null, ex);
        }
        return al;
    }
}