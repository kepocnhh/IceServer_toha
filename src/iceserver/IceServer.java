package iceserver;

import api.API;
import com.itextpdf.text.DocumentException;
import ice.BaseMessage;
import ice.Strings;
import ice.user;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
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
    //
    static private void test()
    {
        user authuser = new user("Инна\tКозлова\tСергеевна\t1\t1\t1\t1\tfalse");
        Date date = authuser.GetDate();
        int year = date.getYear() + 1900;
        int mounth = date.getMonth() + 1;
        int day = date.getDate();
        String dir = IceServer.logpath + year + "/" + mounth + "/" + day;//директория сегодняшних логов
        System.out.println(new Date().toString() +"CreateLogDirName " +dir);
        new File(dir).mkdirs();//создаём эти директории
        String pdfdir = dir + "/" + "pdf";//путь к отчётам PDF
        System.out.println(new Date().toString() +"pdfdir " +pdfdir);
        new File(pdfdir).mkdirs();
        String photodir = dir + "/" + "photo";//путь к фотографиям
        System.out.println(new Date().toString() +"photodir " +photodir);
        new File(photodir).mkdirs();
        String filename = authuser.GetSurname() + " " + day + "." + mounth + "." + year;//создаём имя для файла лога от пользователя
        System.out.println(new Date().toString() +"filename " +filename);
        String fullname = dir + "/" + filename;//полный путь до файла лога
        System.out.println(new Date().toString() +"fullname " +fullname);
        List<BaseMessage> loglist = null;
        try
        {
            File f = new File(fullname);
            if (!f.exists())
            {
                f.createNewFile();
                System.out.println(new Date().toString() + " createNewFile " + fullname);
            }
                System.out.println(new Date().toString() + " Get_BM_List begin");
            loglist = API.Get_BM_List(fullname); //список с данными пользователей
                System.out.println(new Date().toString() + " Get_BM_List OK");
        }
        catch (Exception ex)
        {
            System.out.println(new Date().toString() + " " + ex.toString());
            return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
        }
        if(loglist == null)//и если чтение прошло успешно то продолжаем
        {
            return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
        }
        if(authuser != null)//и если чтение прошло успешно то продолжаем
        {
            return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
        }
    }
    //
    /**
     * @param args the command line arguments
     * args[0] - PORT (config file PORT.txt)
     */
    static public String accpath;
    static public String logpath;
    static public String version;
    static public String toreg;
    static public Strings StringsConfigBM;
    static public String bugreportmail;
    static public Date actual_date;
    //
    //
    static public void SetProp(String t,String a,String l,String v,String scf,String brm, Date d) throws IOException
    {
        accpath=a;
        logpath=l;
        version=v;
        toreg=t;
        StringsConfigBM = new Strings(scf);
        bugreportmail = brm;
        actual_date = d;
    }
    public static void main(String[] args) throws IOException, DocumentException
    {
        if(args.length!=1)
        {
            System.out.println("args[0] - path port file");
            return;
        }
        List<String> al = GetArgsList(
                args[0]
                //"G:\\Google Drive\\Stanley_projects\\NETBEANS\\IceServer_toha\\IceServer\\system2"
                );
        if(al.size() != 11)
        {
            System.out.println("in port file" + "\n" +
                    "args[0] - accounts path" + "\n" +
                    "args[1] - to reg path" + "\n" +
                    "args[2] - log path" + "\n" +
                    "args[3] - version" + "\n" +
                    "args[4] - config path" + "\n" +
                    "args[5] - font path" + "\n" +
                    "args[6] - mail list" + "\n" +
                    "args[7] - your mail data" + "\n" +
                    "args[8] - your bugreport mail" + "\n" +
                    "args[9] - port"
                    );
            return;
        }
            SetProp(al.get(1), al.get(2), al.get(3), al.get(4), al.get(5), al.get(9), new Date());
            System.out.println("IceServer.SetProp good");
            api.CreatePDF.SetProp(al.get(6));
            System.out.println("CreatePDF.SetProp good");
            api.SendEmail.SetProp(al.get(8).split(" ")[0], al.get(8).split(" ")[1], al.get(7).split(" "));
            System.out.println("SendEmail.SetProp good");
            String debugpath = al.get(3) + "DEBUG_" + (actual_date.getYear()+1900) + "." + (actual_date.getMonth()+1) + "." + actual_date.getDate()+ ".txt";
            System.out.println("Log will write to [ " + debugpath + " ]");
        PrintStream st = new PrintStream(new FileOutputStream(debugpath,true));
        System.setErr(st);
        System.setOut(st);
        ServerSocket s = new ServerSocket(Integer.parseInt(al.get(10)));
        System.out.println(new Date().toString() +"\n\n-\tServer Started");
        //
        //test();
        //
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