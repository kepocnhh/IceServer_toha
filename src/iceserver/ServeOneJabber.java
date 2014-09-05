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
import javax.mail.MessagingException;

/**
 *
 * @author alex
 */
public class ServeOneJabber extends Thread
{
//Поля////////////////////////////////////////////////////////////////////////////////////
    private Socket socket;
    
//Конструкторы/////////////////////////////////////////////////////////////////////////////
    public ServeOneJabber(Socket s) throws IOException
    {
        socket = s;
        start(); // вызываем run()
    }
    
//Методы//////////////////////////////////////////////////////////////////////////////////
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
    //ДО АВТОРИЗАЦИИ
    private void Messaging() throws IOException, MessagingException, DocumentException, ClassNotFoundException
    {
        System.out.println(new Date().toString() + " Messaging()");
        ObjectInputStream inputStream = new ObjectInputStream(socket.getInputStream());
        ObjectOutputStream outputStream = new ObjectOutputStream(socket.getOutputStream());
            BaseMessage bm;
            while ((bm = (BaseMessage) inputStream.readObject()) != null)
            {
                Class c = bm.getClass();
                if (c == LastMessage.class)//принятый объект является уведомлением об окончании связи между клиентом и сервером
                {
                    System.out.println(new Date().toString() + " LastMessage");
                    return;
                }
                if (c == BaseMessage.class)//принято сообщение о том, что клиентское приложение запущено 
                {
                    if(bm.getTypeMessage() == BaseMessage.TypeMessage.notification)
                    {
                        System.out.println(new Date().toString() + " BaseMessage");
                        if (bm.GetVersion().equals(IceServer.version))//если версия клиентского приложения актуальна
                        {
                            outputStream.writeObject((BaseMessage) new Strings(IceServer.StringsConfigFile));//отправляем клиенту объект с необходимой информацией
                            System.out.println(" device ON");
                        }
                        else//а если нет
                        {
                            outputStream.writeObject((BaseMessage) new IceError("UsedOldVersion"));//оповещаем клиента о том, что он использует устаревшую версию приложения
                            System.out.println(new Date().toString() + " Еries to use the old version of the library");
                        }
                        continue;
                    }
                    System.out.println(new Date().toString() + " WTF O_o" + "BaseMessage not notification");
                    return;
                }
                //если вы дошли до сюда, значит вы хотите работать с данными пользователей...
                //или вы неведома зверушка
                    List<BaseMessage> userlist = API.Get_BM_List(IceServer.accpath);//список с данными пользователей
                    if(userlist == null)//если списка не существует
                    {
                            userlist = new ArrayList();//его нужно создать
                            API.AddMessage(userlist, IceServer.accpath);//и записать в файл
                    }
                if (c == login.class)//Авторизация пользователя
                {
                    System.out.println(new Date().toString() + " login");
                    user u = API.Get_user(((login) bm).get_log(), userlist);//попытка добыть объект данных пользователя по заданному логину
                    if(u != null)//если добыли
                    {
                        if(u.GetPass().equalsIgnoreCase(((login) bm).get_pass()))//если запрошеный пароль совпадает с паролем найденного пользователя
                        {
                            AuthMessaging(u, outputStream, inputStream);//перехоим в обработку сообщений клиентского приложения от конкретного пользователя
                            return;
                        }
                    }
                    //если не добыли
                        System.out.println(new Date().toString() + " Auth not successful");//нужно вывести сообщение о неуаче
                        outputStream.writeObject((BaseMessage) new IceError("sobed"));//и ответить соответственно клиенту
                    continue;
                }
                if (c == user.class)//Добавление заявки на регистрацию
                {
                    System.out.println(new Date().toString() + " user");
                    user u = API.Get_user(((user) bm).GetMail(), userlist);//попытка добыть объект данных пользователя по заданному логину
                    if(u == null)//если не добыли (это хорошо, потому что мыло не занято)
                    {
                            userlist = API.Get_BM_List(IceServer.toreg);//переделываем список подтвержденных пользователей в список неподтвержденных, который пытаемся достать из файла
                            if(userlist == null)//если списка не существует
                            {
                                userlist = new ArrayList();//его нужно создать
                            }
                            userlist.add(bm);//добавляем в список новобранца
                            API.AddMessage(userlist, IceServer.toreg);//и записываем список в файл
                        System.out.println(new Date().toString() + " Registration successful");
                        SendEmail.sendText(((user) bm).GetMail(), "Регистрация", "Привет от ICENGO!" +"\n" + 
                                "Ваша заявка успешно добавлена и будет обработана в течении нескольких минут." +"\n" +
                                "Спасибо."); //Запилить текст сообщения в файл
                        System.out.println(new Date().toString() + " Send Registration Mail");
                        outputStream.writeObject((BaseMessage) new ping("registrationok"));//оповещаем клиента о том, что всё прошло успешно
                        System.out.println(new Date().toString() + " Registration request send");
                    }
                    else//а если достали
                    {
                        System.out.println(new Date().toString() + " Mail is used.");
                        outputStream.writeObject((BaseMessage) new IceError("mailisused"));//оповещаем клиента о том, что такой электронный адресс уже используется
                        System.out.println(new Date().toString() + " Mail is used send");
                    }
                    continue;
                }
                if (c == forget.class)//принято сообщение о том, что пользователь хочет воостановить пароль
                {
                    System.out.println(new Date().toString() + " Forget");
                    user u = API.Get_user(((forget) bm).GetPing(), userlist);//попытка добыть объект данных пользователя по заданному логину
                    if (u!=null)//если достали
                    {
                        System.out.println(new Date().toString() + " This is password");
                        SendEmail.sendText(u.GetMail(), "Пароль", u.GetPass());//значит можно взять из объекта пароль и отправить его на адрес пользователя
                        System.out.println(new Date().toString() + " Password will be send");
                        outputStream.writeObject((BaseMessage) new ping("forgetok"));//оповещаем клиента о том, что всё прошло успешно
                        System.out.println(new Date().toString() + " Forget request send");
                    }
                    else//а если не достали
                    {
                        outputStream.writeObject((BaseMessage) new IceError("sobed"));//то оповещаем клиента о том, что мы не можем найти пользователя с таким логином
                        System.out.println(new Date().toString() + " ForgetSobed");
                    }
                    continue;
                }
                System.out.println(new Date().toString() + " WTF O_o" + " IN");
            }
        System.out.println(new Date().toString() + " WTF O_o" + " OUT");
    }
    //ПОСЛЕ АВТОРИЗАЦИИ
    private void AuthMessaging(user authuser, ObjectOutputStream outputStream, ObjectInputStream inputStream) throws IOException, ClassNotFoundException, DocumentException, MessagingException
    {
        System.out.println(new Date().toString() + " Auth Successful " + authuser.GetMail());
        
        String dir = CreateLogDirName(authuser, IceServer.logpath);//директория сегодняшних логов
        new File(dir).mkdirs();//создаём эти директории

        String pdfdir = dir + "/" + "pdf";//путь к отчётам PDF
        new File(pdfdir).mkdirs();

        String photodir = dir + "/" + "photo";//путь к фотографиям
        new File(photodir).mkdirs();

        String filename = FileName(authuser);//создаём имя для файла лога от пользователя
        String fullname = dir + "/" + filename;//полный путь до файла лога
        //String StatusSession = "SessionNotOpen";//информация о статусе сессии пользователя
            Itog myitog;//объект итогов пользователя
            List<BaseMessage> loglist = API.Get_BM_List(fullname);//попытка достать список объектов внутри файла логов
            if(loglist != null)//если лист существует
            {
                myitog = API.Get_Itog(authuser.GetMail(), loglist);//попытка достать объект итогов пользователя
                if(myitog != null)//если объект существует
                {
//                    StatusSession = null;
//                    if(myitog.SS==Itog.StatusSession.not_open)//сессия ещё не открывалась
//                    {
//                        StatusSession = "SessionNotOpen";
//                    }
//                    if(myitog.SS==Itog.StatusSession.open)//уже открывалась но не закончилась
//                    {
//                        StatusSession = "SessionAlreadyOpen";
//                    }
//                    if(myitog.SS==Itog.StatusSession.close)//уже закрылась
//                    {
//                        StatusSession = "SessionAlreadyClose"; 
//                    }
//                    if(StatusSession==null)
//                    {
//                        System.out.println(new Date().toString() + "  StatusSessionError " + authuser.GetMail());
//                        outputStream.writeObject((BaseMessage) new ping("StatusSessionError"));//если не одно из условий не выполнится, то необходимо вывести сообщение об ошибке
//                        return;//не позволяем программе дальше обрабатывать информацию
//                    }
                }
                else//если объект не существует
                {
                    myitog = new Itog(authuser.GetMail());//создаём его по умолчанию
                    loglist.add((BaseMessage) myitog);//добавляем в лист
                    API.AddMessage(loglist, fullname);//записываем лист в файл лога
                }
            }
            else//если лист не существует
            {
                loglist = new ArrayList();//создаём пустой лист
                    myitog = new Itog(authuser.GetMail());//создаём объект итогов по умолчанию
                    loglist.add((BaseMessage) myitog);//добавляем в лист
                    API.AddMessage(loglist, fullname);//записываем лист в файл лога
            }
            outputStream.writeObject((BaseMessage) myitog);//отправляем пользователю объект итогов из которого он может взять все необходимые данные
            System.out.println(new Date().toString() + " Itog will be send to " + authuser.GetMail());
            BaseMessage bm;
            while ((bm = (BaseMessage) inputStream.readObject()) != null)
            {
                if (API.Get_BM(bm, loglist)!=null)//порверяем не записывал ли сервер объект с таким же UI
                {
                    //уже есть
                    if(bm.getTypeMessage()==BaseMessage.TypeMessage.add)
                    {
                        //и лезет опять на добавление
                        System.out.println(new Date().toString() + " поймали ещё одного лох-несса " + bm.toString() + 
                                " " + " Logsession " + loglist);
                        return;//не позволяем программе дальше обрабатывать информацию
                    }
                        //и лезет куда-то ещё 0о
                        System.out.println(new Date().toString() + " что-то новенькое 0о " + bm.toString() + 
                                " " + " Logsession " + loglist);
                        return;//не позволяем программе дальше обрабатывать информацию
                }
                
                //Сюда мы с Тошиком напишем реакцию сервера на каждый из классов, которые может принять сервер...
                //И будет нам счастье!
                Class c = bm.getClass();
                if (c == ping.class)//от клиента пришло сообщение о том, что клиент начал работать на определенном этапе
                {
                            loglist.add(bm);//просто добавляем это сообщение в лист объектов лога
                            API.AddMessage(loglist, fullname);//записываем лист в файл лога
                    System.out.println(new Date().toString() + " ping " + ((ping) bm).GetPing() + " " + authuser.GetMail());
                    outputStream.writeObject(bm);//зеркально отвечаем клиенту
                    continue;
                }
                if (c == DataForRecord.class)//пришло сообщение содержащие данные для записи
                {
                    System.out.println(new Date().toString() + " DFR " + authuser.GetMail());
                    DataForRecord p = (DataForRecord) bm;
                    String pdfname = null;
                    if (p.getTypeEvent() == DataForRecord.TypeEvent.open || p.getTypeEvent() == DataForRecord.TypeEvent.close)//и это касается конкретно открытия или закрытия смены 
                    {
                        pdfname = p.nameshop + " " + filename + " " + Translate(p.getTypeEvent());//создаём имя для PDF отчёта
                            loglist.add(bm);//добавляем это сообщение в лист объектов лога
                        myitog = API.Calculate_Itog(myitog, authuser, loglist);//пересчитываем объект итогов
                        loglist = API.Set_Itog(myitog, loglist);//переписываем объект итогов внутри листа объектов лога
                        API.AddMessage(loglist, fullname);//записываем лист в файл лога
                        String mailtext = myitog.day_otw+"\n"+
                                        "Начало рабочего дня "+myitog.date_open.getHours()+":"+CreatePDF.minutes(myitog.date_open.getMinutes()+"");
                        if (p.getTypeEvent() == DataForRecord.TypeEvent.open)//если было открытие
                        {
                            CreatePDF._CreatePDF(new Strings(IceServer.StringsConfigFile), authuser,
                                    p, myitog,
                                    pdfdir + "/" + pdfname);//запускаем метод класса CreatePDF для создания отчёта на открытие
                        }
                        else//а если закрытие
                        {
                            DataForRecord dfropen = API.Get_DFR(DataForRecord.TypeEvent.open, loglist);//пробуем добыть данные с открытия
                            DataForRecord dfrdrug = API.Get_DFR(DataForRecord.TypeEvent.drug, loglist);//приходы
                            DataForRecord dfrsteal = API.Get_DFR(DataForRecord.TypeEvent.steal, loglist);//уходы
                            CreatePDF._CreatePDF(new Strings(IceServer.StringsConfigFile),
                                    authuser,
                                    dfropen,
                                    dfrdrug,
                                    dfrsteal,
                                    p,
                                    myitog,
                                    pdfdir + "/" + pdfname);//запускаем метод класса CreatePDF для создания отчёта на закрытие
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
                            for (String mail : SendEmail.maillist)//отправляем письмо с отчётом и коментарием всем адресам в списке SendEmail.maillist
                            {
                                SendEmail.sendPdf(
                                        mail,
                                        pdfname, //Тема сообщения
                                        mailtext,
                                        pdfdir + "/" + pdfname);
                            }
                            outputStream.writeObject((BaseMessage) myitog);//отправляем итоги клиенту
                            continue;
                    }
                    if (p.getTypeEvent() == DataForRecord.TypeEvent.drug || p.getTypeEvent() == DataForRecord.TypeEvent.steal)//а если всё же пришла дата по приходу или уходу
                    {
                        System.out.println(new Date().toString() + " Is DFR.drug or steal " + authuser.GetMail());
                            DataForRecord tmp = API.Get_DFR(p.getTypeEvent(), loglist);//сначала пытаемся достать то, что уже было
                            if(tmp!=null)//если такое 0_0 было раньше
                            {
                                tmp.addData(p, true);//то складываем 
                            }
                            else//ну а если не было, то принятый объект теперь будет... 
                            {
                                tmp = p;
                            }
                            loglist = API.Set_DFR(tmp, loglist);
                            API.AddMessage(loglist, fullname);
                            System.out.println(new Date().toString() + " " + "DFRrequest " + pdfname);
                            outputStream.writeObject((BaseMessage) myitog);//отправляем итоги клиенту
                            System.out.println(new Date().toString() +" "+ pdfname + " will be sending to " + authuser.GetMail());
                            System.out.println(new Date().toString() + " recordok " + authuser.GetMail());
                            continue;
                    }
                    outputStream.writeObject((BaseMessage) new IceError("sobed"));
                    return;
                }
                if (c == DataCass.class)
                {
                    System.out.println(new Date().toString() + " IsDataCass" + authuser.GetMail());
                            loglist.add(bm);
                            API.AddMessage(loglist, fullname);
                    outputStream.writeObject((BaseMessage) new ping("cassok"));
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
            String path = CreateLogDirName(us, IceServer.logpath);
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