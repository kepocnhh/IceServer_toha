package iceserver;

import api.API;
import api.CreatePDF;
import api.SendEmail;
import com.itextpdf.text.DocumentException;
import ice.*;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.mail.MessagingException;

/**
 *
 * @author alex
 */
public class ServeOneJabber extends Thread
{
//Поля//////////////////////////////////////////////////////////////////////
    private Socket socket;
    
//Конструкторы//////////////////////////////////////////////////////////////
    public ServeOneJabber(Socket s) throws IOException
    {
        socket = s;
        start(); // вызываем run()
    }
    
//Методы////////////////////////////////////////////////////////////////////
    public void run()
    {
        try
        {
            Messaging();
        } catch (MessagingException ex) {
        } catch (DocumentException ex) {
        } catch (ClassNotFoundException ex) {
        } catch (IOException ex) {
        }
        finally
        {
            try
            {
                socket.close();
            } 
            catch (IOException ex) 
            {
                
            }
        }
    }
    
    //Реализация обработки сообщений и бизнес-логика
    private void Messaging() throws IOException, MessagingException, DocumentException, ClassNotFoundException
    {
        System.out.println(new Date().toString() + " Messaging()");
            ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
                BaseMessage bm;
                while ((bm = (BaseMessage) inputStream.readObject()) != null)
                {
                    Class c = bm.getClass();
                    if (c == login.class)//Авторизация пользователя
                    {
                        System.out.println(new Date().toString() + " login");
                        user u = API.Messaging((login) bm);
                        if (u!=null)
                        {
                            AuthMessaging(u, outputStream, inputStream);
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
                            outputStream.writeObject((BaseMessage) new Strings(API.StringsConfigFile));
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
        //System.out.println(new Date().toString() + " WTF O_o");
        //BaseMessage bad = (BaseMessage) new ping("GetOut!");
    }

    private void AuthMessaging(user authuser, ObjectOutputStream outputStream, ObjectInputStream inputStream) throws IOException, ClassNotFoundException, DocumentException, MessagingException
    {
        System.out.println(new Date().toString() + " Auth Successful " + authuser.GetMail());
        
        String dir = CreateLogDirName(authuser, API.logpath);
        File myPath = new File(dir);
        myPath.mkdirs();

        String pdfdir = dir + "/" + "pdf";
        File PdfPath = new File(pdfdir);
        PdfPath.mkdirs();

        String photodir = dir + "/" + "photo";
        File photoPath = new File(photodir);
        photoPath.mkdirs();

        String filename;
        String StatusSession = "SessionNotOpen";
            List<BaseMessage> itoglist = API.GetBM_List(dir+ "/Itog");
            Itog myitog = API.Get_Itog(authuser.GetMail(), itoglist);
            if(itoglist != null)
            {
                if(myitog != null)
                {
                    if(myitog.SS==Itog.StatusSession.open)
                    {
                        StatusSession = "SessionAlreadyOpen"; 
                    }
                    if(myitog.SS==Itog.StatusSession.close)
                    {
                        StatusSession = "SessionAlreadyClose"; 
                    }
                }
                else
                {
                    myitog = new Itog(authuser.GetMail());
                    itoglist.add((BaseMessage) myitog);
                    API.AddMessage(itoglist, dir+ "/Itog");
                }
            }
            else
            {
                itoglist = new ArrayList();
                    myitog = new Itog(authuser.GetMail());
                    itoglist.add((BaseMessage) myitog);
                    API.AddMessage(itoglist, dir+ "/Itog");
            }
        filename = FileName(authuser);
        String fullname = dir + "/" + filename;
            outputStream.writeObject((BaseMessage) new ping(StatusSession));
            System.out.println(new Date().toString() + " " + StatusSession + " StatusSession will be send to " + authuser.GetMail());
            BaseMessage bm;
            while ((bm = (BaseMessage) inputStream.readObject()) != null)
            {
                List<BaseMessage> logsession = API.GetBM_List(fullname);
                if (API.Find_BM(bm, fullname)!=null)
                {
                    //уже есть
                    if(bm.getTypeMessage()==BaseMessage.TypeMessage.add)
                    {
                        //и лезет опять на добавление
                        System.out.println(new Date().toString() + " поймали ещё одного лох-несса " + bm.toString() + 
                                " " + " Logsession " + logsession);
                        return;
                    }
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
                            CreatePDF._CreatePDF(new Strings(API.StringsConfigFile), authuser,
                                    p, myitog,
                                    pdfdir + "/" + pdfname);
                        }
                        else
                        {
                            DataForRecord dfropen = API.Get_DFR(DataForRecord.TypeEvent.open, loglist);
                            DataForRecord dfrdrug = API.Get_DFR(DataForRecord.TypeEvent.drug, loglist);
                            DataForRecord dfrsteal = API.Get_DFR(DataForRecord.TypeEvent.steal, loglist);
                            CreatePDF._CreatePDF(new Strings(API.StringsConfigFile),
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
                            for (String mail : SendEmail.maillist)
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
                            System.out.println(new Date().toString() + " " + "DFRrequest " + pdfname);
                            outputStream.writeObject((BaseMessage) new ping("recordok"));
                            System.out.println(new Date().toString() +" "+ pdfname + " will be sending to " + authuser.GetMail());
                            System.out.println(new Date().toString() + " recordok " + authuser.GetMail());
                            continue;
                    }
                    outputStream.writeObject((BaseMessage) new ping("ERRORDFR"));
                    return;
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
            String path = CreateLogDirName(us, API.logpath);
            String ls[] = new File(path).list();
            return FileName + " " + (ls.length - 3);//DEBUG для супера
        }
    }

    private static String Translate(DataForRecord.TypeEvent typeEvent)
    {
        if (typeEvent == DataForRecord.TypeEvent.open)
        {
            return "Открытие";
        }
        if (typeEvent == DataForRecord.TypeEvent.close)
        {
            return "Закрытие";
        }
        return "";
    }
}