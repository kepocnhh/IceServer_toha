package iceserver;

import api.API;
import api.CreatePDF;
import api.SendEmail;
import com.itextpdf.text.DocumentException;
import ice.*;
import ice.DataForRecord.TypeEvent;
import java.io.*;

import java.net.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;

/**
 *
 * @author alex
 */
public class ServeOneJabber extends Thread
{
//Поля//////////////////////////////////////////////////////////////////////
    private Socket socket;
    String toreg;
    String accounts;
    String logdir;
    static String fonts;
    static String version;
    static List<String> maillist;
    static List<String> salarylist;
    static String StringsConfigFile;
    static int timeCorrect;
    
//Конструкторы//////////////////////////////////////////////////////////////
    public ServeOneJabber(Socket s, String toreg, String accounts, String logdir, String fonts, String version, String maillist, String salarylist, String StringsConfigFile, String timeCorrect) throws IOException
    {
        socket = s;
        this.toreg = toreg;
        this.accounts = accounts;
        this.logdir = logdir;
        this.fonts = fonts;

        this.version = version;
        this.maillist = GetMailList(maillist);
        this.salarylist = GetSalaryList(salarylist);
        this.StringsConfigFile = StringsConfigFile;
        this.timeCorrect = Integer.parseInt(timeCorrect);

        start(); // вызываем run()
    }
    
//Методы////////////////////////////////////////////////////////////////////
    public void run()
    {
        try
        {
            Messaging();
            return;
        }
        catch (IOException e)
        {
            System.err.println("IO Exception " + e.toString());
        } catch (MessagingException ex) {
            Logger.getLogger(ServeOneJabber.class.getName()).log(Level.SEVERE, null, ex);
        } catch (DocumentException ex) {
            Logger.getLogger(ServeOneJabber.class.getName()).log(Level.SEVERE, null, ex);
        }
        finally
        {
            try
            {
                socket.close();
            }
            catch (IOException e)
            {
                System.err.println("Socket not closed " + e.toString());
            }
        }
    }
    
    //Реализация обработки сообщений и бизнес-логика
    private void Messaging() throws IOException, MessagingException, DocumentException
    {
        System.out.println(new Date().toString() + " Messaging()");
        try
        {
            ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
            try
            {
                BaseMessage bm;
                while ((bm = (BaseMessage) inputStream.readObject()) != null)
                {
                    Class c = bm.getClass();
                    if (c == login.class)//Авторизация пользователя
                    {
                        System.out.println(new Date().toString() + " login");
                        login log = API.Messaging((login) bm);
                        if (log!=null)
                        {
                            AuthMessaging(log.GetUser(accounts), outputStream, inputStream);
                            return;
                        }
                        else
                        {
                            System.out.println(new Date().toString() + " Auth not successful");
                            outputStream.writeObject((BaseMessage) new ping("sobed"));
                        }
                        continue;
                    }
                    if (c == ping.class)
                    {
                        System.out.println(new Date().toString() + " ping");
                        ping p = API.Messaging((ping) bm);
                        if (p!=null)
                        {
                            outputStream.writeObject((BaseMessage) new Strings(StringsConfigFile));
                            System.out.println(p.GetPing() + " device ON");
                        }
                        else
                        {
                            outputStream.writeObject((BaseMessage) new ping("UsedOldVersion"));
                            System.out.println(new Date().toString() + " Еries to use the old version of the library");
                        }
                        continue;
                    }
                    if (c == user.class)//Добавление заявки на регистрацию
                    {
                        System.out.println(new Date().toString() + " user");
                        user us = API.Messaging((user) bm);
                        if (us!=null)
                        {
                            System.out.println(new Date().toString() + " Registration successful");
                            SendEmail.sendText(us.GetMail(), "Регистрация", "Привет от ICENGO! \nВаша заявка успешно добавлена и будет обработана в течении нескольких минут. \nСпасибо."); //Запилить текст сообщения в файл
                            System.out.println(new Date().toString() + " Send Registration Mail");
                            outputStream.writeObject((BaseMessage) new ping("registrationok"));
                            System.out.println(new Date().toString() + " Registration request send");
                        }
                        else
                        {
                            System.out.println(new Date().toString() + " Mail is used.");
                            outputStream.writeObject((BaseMessage) new ping("mailisused"));
                            System.out.println(new Date().toString() + " Mail is used send");
                        }
                        continue;
                    }
                    if (c == forget.class)
                    {
                        System.out.println(new Date().toString() + " Forget");
                        forget f = (forget) bm;
                        user u = API.Messaging(f);
                        if (u!=null)
                        {
                            System.out.println(new Date().toString() + " This is password");
                            SendEmail.sendText(u.GetMail(), "Пароль", u.GetPass());
                            System.out.println(new Date().toString() + " Password will be send");
                            outputStream.writeObject((BaseMessage) new ping("forgetok"));
                            System.out.println(new Date().toString() + " Forget request send");
                        }
                        else
                        {
                            outputStream.writeObject((BaseMessage) new ping("forgetok"));
                            System.out.println(new Date().toString() + " ForgetSobed");
                        }
                    }
                    if (c == LastMessage.class)
                    {
                        System.out.println(new Date().toString() + " LastMessage");
                        return;
                    }
                }
                return;
            }
            catch (ClassNotFoundException ex)
            {
                Logger.getLogger(ServeOneJabber.class.getName()).log(Level.SEVERE, null, ex);
                System.err.println("Check your BaseMessage lib.");
            }
            return;
        }
        catch (IOException e)
        {
            System.err.println("IO Exception in Messaging! " + e.toString());
        }
        System.out.println(new Date().toString() + " WTF O_o");
        BaseMessage bad = (BaseMessage) new ping("GetOut!");
    }
    
    private static List<DataForRecord> FindEventInLog(DataForRecord.TypeEvent typeEvent, List<BaseMessage> loglist)
    {
        System.out.println(new Date().toString() + " DFR FindEventInLog");

        List<DataForRecord> arraydfr = new ArrayList();
        for (BaseMessage bm : loglist)
        {
            Class c = bm.getClass();
            if (c == DataForRecord.class)
            {
                DataForRecord dfr = (DataForRecord) bm;
                if (dfr.getTypeEvent() == typeEvent)
                {
                    arraydfr.add(dfr);
                }
            }
        }
        return arraydfr;
    }

    private static List<DataCass> FindEventInLog(DataCass.TypeEvent typeEvent, List<BaseMessage> loglist)
    {
        System.out.println(new Date().toString() + " Datacass FindEventInLog");

        List<DataCass> arraydc = new ArrayList();
        for (BaseMessage bm : loglist)
        {
            Class c = bm.getClass();
            if (c == DataCass.class)
            {
                DataCass dc = (DataCass) bm;
                if (dc.getTypeEvent() == typeEvent)
                {
                    arraydc.add(dc);
                }
            }
        }
        return arraydc;
    }

    static private double getresultmass(double[] open, double[] drug, double[] steal, double[] close, int i)
    {
        return open[i] + drug[i] - steal[i] - close[i];
    }

    private void AuthMessaging(user authuser, ObjectOutputStream outputStream, ObjectInputStream inputStream) throws IOException, ClassNotFoundException, DocumentException, MessagingException
    {
        System.out.println(new Date().toString() + " Auth Successful " + authuser.GetMail());

        //System.out.println(new Date().toString() + " Создание папки для пользователя " + authuser.GetMail());
        String dir = CreateLogDirName(authuser, logdir);
        File myPath = new File(dir);
        myPath.mkdirs();

        String pdfdir = dir + "/" + "pdf";
        File PdfPath = new File(pdfdir);
        PdfPath.mkdirs();

        String photodir = dir + "/" + "photo";
        File photoPath = new File(photodir);
        photoPath.mkdirs();

        String filename;
        BaseMessage StatusSession = (BaseMessage) new ping("ErrorStatusSession"); 
            List<BaseMessage> itoglist = API.GetBM_List(dir+ "/Itog");
            Itog myitog = API.Get_Itog(authuser.GetMail(), itoglist);
            if(itoglist != null)
            {
                if(myitog != null)
                {
                    if(myitog.SS==Itog.StatusSession.not_open)
                    {
                        StatusSession = (BaseMessage) new ping("SessionNotOpen");
                    }
                    if(myitog.SS==Itog.StatusSession.open)
                    {
                        StatusSession = (BaseMessage) new ping("SessionAlreadyOpen"); 
                    }
                    if(myitog.SS==Itog.StatusSession.close)
                    {
                        StatusSession = (BaseMessage) new ping("SessionAlreadyClose"); 
                    }
                }
                else
                {
                    myitog = new Itog(authuser.GetMail());
                    itoglist.add((BaseMessage) myitog);
                    API.AddMessage(itoglist, dir+ "/Itog");
                    StatusSession = (BaseMessage) new ping("SessionNotOpen");
                }
            }
            else
            {
                    itoglist = new ArrayList();
                myitog = new Itog(authuser.GetMail());
                itoglist.add((BaseMessage) myitog);
                API.AddMessage(itoglist, dir+ "/Itog");
                StatusSession = (BaseMessage) new ping("SessionNotOpen");
            }
        filename = FileName(authuser);
        String fullname = dir + "/" + filename;
            outputStream.writeObject(StatusSession);
            System.out.println(new Date().toString() + " " + ((ping) StatusSession).GetPing() + " StatusSession will be send to " + authuser.GetMail());
            BaseMessage bm;
            BaseMessage request;
            while ((bm = (BaseMessage) inputStream.readObject()) != null)
            {
                List<BaseMessage> logsession = API.GetBM_List(fullname);
                if (API.Find_BM(bm, fullname)!=null)
                {
                    //уже есть
                    System.out.println(new Date().toString() + " поймали ещё одного лох-несса " + bm.toString() + 
                            " " + " Logsession " + logsession);
                    return;
                }
                
                //Сюда мы с Тошиком напишем реакцию сервера на каждый из классов, 
                //которые может принять сервер. И будет нам счастье!
                Class c = bm.getClass();
                if (c == ping.class)
                {
                    API.AddMessage(bm, fullname);
                    System.out.println(new Date().toString() + " ping " + ((ping) bm).GetPing() + " " + authuser.GetMail());
                    outputStream.writeObject(bm);
                    continue;
                }
                if (c == DataForRecord.class)
                {
                    System.out.println(new Date().toString() + " DFR " + authuser.GetMail());
                    DataForRecord p = (DataForRecord) bm;
                    String pdfname = "";
                    List<BaseMessage> loglist = API.GetBM_List(fullname);
                            if (loglist == null)
                            {
                                System.out.println(new Date().toString() + " " + "DFRrequest " + pdfname + " to " + authuser.GetMail());
                                outputStream.writeObject((BaseMessage) new ping("LogListIsEmpty"));
                                continue;
                            }
                    if (p.getTypeEvent() == DataForRecord.TypeEvent.open || p.getTypeEvent() == DataForRecord.TypeEvent.close)
                    {
                        pdfname = p.nameshop + " " + filename + " " + Translate(p.getTypeEvent());
                            loglist.add(bm);
                            API.AddMessage(loglist, fullname);
                        myitog = API.Calculate_Itog(myitog, authuser, loglist);
                        itoglist = API.Set_Itog(myitog, itoglist);
                        API.AddMessage(itoglist, dir+ "/Itog");
                        String 
                            mailtext = myitog.day_otw+"\n"+
                                        "Начало рабочего дня "+myitog.date_open.getHours()+":"+CreatePDF.minutes(myitog.date_open.getMinutes()+"");
                        if (p.getTypeEvent() == DataForRecord.TypeEvent.open)
                        {
                            CreatePDF._CreatePDF(new Strings(StringsConfigFile), authuser,
                                    p, myitog,
                                    pdfdir + "/" + pdfname);
                        }
                        else
                        {
                            DataForRecord dfropen = API.Get_DFR(DataForRecord.TypeEvent.open, loglist);
                            DataForRecord dfrdrug = API.Get_DFR(DataForRecord.TypeEvent.drug, loglist);
                            DataForRecord dfrsteal = API.Get_DFR(DataForRecord.TypeEvent.steal, loglist);
                            CreatePDF._CreatePDF(new Strings(StringsConfigFile),
                                    authuser,
                                    dfropen,
                                    dfrdrug,
                                    dfrsteal,
                                    p,
                                    myitog,
                                    pdfdir + "/" + pdfname);
                            mailtext += "\n"+
                                "Конец рабочего дня "+myitog.date_close.getHours()+":"+CreatePDF.minutes(myitog.date_close.getMinutes()+"")+"\n"+
                                "--------------------"+"\n"+
                                "Продано кепок "+myitog.amount_k+"\n"+
                                "Выручка за кепки "+myitog.amount_k*authuser.price_k+"\n"+
                                "-"+"\n"+
                                "Продано стаканов "+myitog.amount_s+"\n"+
                                "Выручка за стаканы "+myitog.amount_s*authuser.price_s+"\n"+
                                "-"+"\n"+
                                "Всего выручка "+(myitog.amount_t*authuser.price_t+myitog.amount_k*authuser.price_k+myitog.amount_s*authuser.price_s)+"\n"+
                                "--------------------"+"\n"+
                                "Сумма бонуса "+myitog.amount_k*authuser.bonus+"\n"+
                                "--------------------"+"\n"+
                                "Вес кепок "+myitog.amount_k*authuser.weight_k+"\n"+
                                "Вес стаканов "+myitog.amount_s*authuser.weight_s+"\n"+
                                "-"+"\n"+
                                "Итого ВЕС "+(myitog.amount_t*authuser.weight_t+myitog.amount_k*authuser.weight_k+myitog.amount_s*authuser.weight_s)+"\n"+
                                "--------------------"+"\n"+
                                "Оклад "+myitog.salary+"\n"+
                                "ИТОГО ЗП "+((myitog.salary+myitog.amount_k*authuser.bonus)-myitog.get_summ_mulct());
                        }
                            for (String mail : maillist)
                            {
                                SendEmail.sendPdf(
                                        mail,
                                        pdfname, //Тема сообщения
                                        mailtext,
                                        pdfdir + "/" + pdfname);
                            }
                            outputStream.writeObject((BaseMessage) myitog);
                            continue;
                    }
                    /////////////////////////////////////////////
                    if (p.getTypeEvent() == DataForRecord.TypeEvent.open)
                    {
                        System.out.println(new Date().toString() + " Is DFR.open " + authuser.GetMail());
                            pdfname = p.nameshop + " " + filename + " " + Translate(p.getTypeEvent());
                            System.out.println(new Date().toString() + " Create pdf " + authuser.GetMail());
                                System.out.println(new Date().toString() + " " + TypeEvent.open.toString());
                            myitog = API.Calculate_Itog(myitog, authuser, loglist);
                            itoglist = API.Set_Itog(myitog, itoglist);
                            API.AddMessage(itoglist, dir+ "/Itog");
                            CreatePDF._CreatePDF(new Strings(StringsConfigFile), authuser,
                                    p, myitog,
                                    pdfdir + "/" + pdfname);
                            System.out.println(new Date().toString() + " EmbeddedImageEmailUtil " + authuser.GetMail());
                            for (String mail : maillist)
                            {
                                SendEmail.sendPdf(
                                        mail,
                                        pdfname, //Тема сообщения
                                        myitog.day_otw+"\n"+
                                        "Начало рабочего дня "+myitog.date_open.getHours()+":"+CreatePDF.minutes(myitog.date_open.getMinutes()+""),
                                        pdfdir + "/" + pdfname);
                                System.out.println(new Date().toString() + " " + pdfname + " send to " + mail);
                            }
                            loglist.add(bm);
                            API.AddMessage(loglist, fullname);
                            System.out.println(new Date().toString() + " " + "DFRrequest " + pdfname);
                            outputStream.writeObject((BaseMessage) new ping("recordok"));
                            System.out.println(new Date().toString() + " Recordok will be send to " + authuser.GetMail());
                            continue;
                    }
                    if (p.getTypeEvent() == DataForRecord.TypeEvent.close)
                    {
                        System.out.println(new Date().toString() + " Ss DFR.close " + authuser.GetMail());
                            System.out.println(new Date().toString() + " GetDFR " + authuser.GetMail());
                            DataForRecord dfropen = API.Get_DFR(DataForRecord.TypeEvent.open, loglist);
                            DataForRecord dfrdrug = API.Get_DFR(DataForRecord.TypeEvent.drug, loglist);
                            DataForRecord dfrsteal = API.Get_DFR(DataForRecord.TypeEvent.steal, loglist);
                            pdfname = dfropen.nameshop + " " + filename + " " + Translate(p.getTypeEvent());
                            System.out.println(new Date().toString() + " CreatePDF " + authuser.GetMail());
                            myitog = API.Calculate_Itog(myitog, authuser, loglist);
                            if(authuser.GetSuper())
                            {
                                myitog.SS = Itog.StatusSession.not_open;
                            }
                            itoglist = API.Set_Itog(myitog, itoglist);
                            API.AddMessage(itoglist, dir+ "/Itog");
                            CreatePDF._CreatePDF(new Strings(StringsConfigFile),
                                    authuser,
                                    dfropen,
                                    dfrdrug,
                                    dfrsteal,
                                    p,
                                    myitog,
                                    pdfdir + "/" + pdfname);
                            for (String mail : maillist)
                            {
                                SendEmail.sendPdf(mail, pdfname,
                                myitog.day_otw+"\n"+
                                "Начало рабочего дня "+myitog.date_open.getHours()+":"+CreatePDF.minutes(myitog.date_open.getMinutes()+"")+"\n"+
                                "Конец рабочего дня "+myitog.date_close.getHours()+":"+CreatePDF.minutes(myitog.date_close.getMinutes()+"")+"\n"+
                                "--------------------"+"\n"+
                                "Продано кепок "+myitog.amount_k+"\n"+
                                "Выручка за кепки "+myitog.amount_k*authuser.price_k+"\n"+
                                "-"+"\n"+
                                "Продано стаканов "+myitog.amount_s+"\n"+
                                "Выручка за стаканы "+myitog.amount_s*authuser.price_s+"\n"+
                                "-"+"\n"+
                                "Всего выручка "+(myitog.amount_t*authuser.price_t+myitog.amount_k*authuser.price_k+myitog.amount_s*authuser.price_s)+"\n"+
                                "--------------------"+"\n"+
                                "Сумма бонуса "+myitog.amount_k*authuser.bonus+"\n"+
                                "--------------------"+"\n"+
                                "Вес кепок "+myitog.amount_k*authuser.weight_k+"\n"+
                                "Вес стаканов "+myitog.amount_s*authuser.weight_s+"\n"+
                                "-"+"\n"+
                                "Итого ВЕС "+(myitog.amount_t*authuser.weight_t+myitog.amount_k*authuser.weight_k+myitog.amount_s*authuser.weight_s)+"\n"+
                                "--------------------"+"\n"+
                                "Оклад "+myitog.salary+"\n"+
                                "ИТОГО ЗП "+((myitog.salary+myitog.amount_k*authuser.bonus)-myitog.get_summ_mulct())
                                ,pdfdir + "/" + pdfname);
                                System.out.println(new Date().toString() + " " + pdfname + " send to " + mail);

                            }
                            loglist.add(bm);
                            API.AddMessage(loglist, fullname);
                            pdfname = "recordok";
                            System.out.println(new Date().toString() + " " + "DFRrequest " + pdfname);
                            
                            outputStream.writeObject((BaseMessage) new ping(pdfname));
                            System.out.println(new Date().toString() + " recordok will be send to " + authuser.GetMail());
                            continue;
                    }
                    if (p.getTypeEvent() == DataForRecord.TypeEvent.drug || p.getTypeEvent() == DataForRecord.TypeEvent.steal)
                    {
                        System.out.println(new Date().toString() + " Is DFR.drug or steal " + authuser.GetMail());
                            DataForRecord tmp = API.Get_DFR(p.getTypeEvent(), loglist);
                            if(tmp!=null)
                            {
                                if(p.getTypeEvent() == DataForRecord.TypeEvent.drug)
                                {
                                    tmp.addData(p, true);
                                }
                                else
                                {
                                    tmp.addData(p, false);
                                }
                            }
                            else
                            {
                                tmp = p;
                            }
                            bm = (BaseMessage)tmp;
                            loglist = API.Set_DFR(tmp, loglist);
                            API.AddMessage(loglist, fullname);
                            pdfname = "recordok";
                            System.out.println(new Date().toString() + " recordok " + authuser.GetMail());
                    }
                    System.out.println(new Date().toString() + " " + "DFRrequest " + pdfname);
                    outputStream.writeObject((BaseMessage) new ping(pdfname));
                    System.out.println(new Date().toString() +" "+ pdfname + " will be sending to " + authuser.GetMail());
                    continue;
                }
                    /////////////////////////////////////////////
                if (c == DataCass.class)
                {
                    System.out.println(new Date().toString() + " IsDataCass" + authuser.GetMail());
                    outputStream.writeObject((BaseMessage) new ping("cassok"));
                    API.AddMessage(bm, fullname);
                    System.out.println(new Date().toString() + " cassok");
                    continue;
                }
                if (c == LastMessage.class)
                {
                    System.out.println(new Date().toString() + " Бабай!");
                    return;
                }
                else
                {
                    System.out.println(new Date().toString() + " Ну и ладно...");
                    return;
                }

            }
        return;
    }

    private String CreateLogDirName(user us, String logDirPath)
    {
        Date date = us.GetDate();   //DEBUG учесть часовой пояс!
        String mail = us.GetMail();
        String surname = us.GetSurname();

        int year = date.getYear() + 1900;
        int mounth = date.getMonth() + 1;
        int day = date.getDate();

        if (!us.GetSuper())
        {
            return logDirPath + year + "/" + mounth + "/" + day;
        }
        else
        {
            String path = logDirPath + year + "/" + mounth + "/" + day + "/" + "supers" + "/" + mail;
            return path;
        }
    }

    private String FileName(user us)
    {
        Date date = us.GetDate();   //DEBUG учесть часовой пояс!
        String mail = us.GetMail();
        String surname = us.GetSurname();

        int year = date.getYear() + 1900;
        int mounth = date.getMonth() + 1;
        int day = date.getDate();

        String FileName = surname + " " + day + "." + mounth + "." + year;

        if (!us.GetSuper())
        {
            return FileName;
        }
        else
        {
            String path = CreateLogDirName(us, logdir);
            String ls[] = new File(path).list();
            return FileName + " " + (ls.length - 3);    //DEBUG для супера
        }
    }

    private static String Translate(TypeEvent typeEvent)
    {
        if (typeEvent == TypeEvent.open)
        {
            return "Открытие";
        }
        if (typeEvent == TypeEvent.close)
        {
            return "Закрытие";
        }
        return "";
    }

    private List<String> GetMailList(String maillist)
    {
        List<String> mlist = null;
        try
        {
            BufferedReader br = new BufferedReader(new FileReader(maillist));
            String str;
            mlist = new ArrayList<String>();
            while ((str = br.readLine()) != null)
            {
                mlist.add(str);
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
        return mlist;
    }

    private List<String> GetSalaryList(String salarylist)
    {
        List<String> slist = null;
        try
        {
            BufferedReader br = new BufferedReader(new FileReader(salarylist));
            String str;
            slist = new ArrayList<String>();
            while ((str = br.readLine()) != null)
            {
                slist.add(str);
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
        return slist;
    }

    private double GetSalary(List<String> salarylist, String mail)
    {
        double s = Double.parseDouble((salarylist.get(0)).split("\t")[0]);
        for (String string : salarylist)
        {
            String[] insplits = string.split("\t");
            if (insplits[0].equals(mail))
            {
                s = Integer.parseInt(insplits[1]);
            }
        }
        return s;
    }
}
