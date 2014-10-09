package iceserver;

import api.API;
import api.CreatePDF;
import api.SendEmail;
import com.itextpdf.text.DocumentException;
import ice.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
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
        Messaging();
        try
        {
            socket.close();
        }
        catch (IOException ex)
        {
            System.out.println(new Date().toString() + " WTF O_o" + " Socket");
            System.out.println(new Date().toString() + ex.toString());
        }
    }
    
    //Реализация обработки сообщений и бизнес-логика
    //ДО АВТОРИЗАЦИИ
    private void Messaging()
    {
        System.out.println(new Date().toString() + " Messaging()");
        ObjectInputStream inputStream;
        ObjectOutputStream outputStream;
        try
        {
            inputStream = new ObjectInputStream(socket.getInputStream());
            outputStream = new ObjectOutputStream(socket.getOutputStream());
        }
        catch (IOException ex)
        {
            System.out.println(new Date().toString() + " WTF O_o" + " Messaging");
            System.out.println(new Date().toString() + ex.toString());
            return;//не позволяем программе дальше обрабатывать информацию
        }
            BaseMessage bm;
            while (true)
            {
                try
                {
                    bm = (BaseMessage) inputStream.readObject();
                }
                catch (IOException ex)
                {
                    System.out.println(new Date().toString() + " WTF O_o" + " проблема с чтением объекта");
                    System.out.println(new Date().toString() + ex.toString());
                    Answer(BaseMessage.class, outputStream, (BaseMessage) new IceError("ReadObject"),"неудачная попытка ответить клиенту что проблема с чтением объекта");//оповещаем клиента о том, что проблема с чтением объекта
                    return;//не позволяем программе дальше обрабатывать информацию
                }
                catch (ClassNotFoundException ex)
                {
                    System.out.println(new Date().toString() + " WTF O_o" + " проблемма с классами");
                    System.out.println(new Date().toString() + ex.toString());
                    Answer(BaseMessage.class, outputStream, (BaseMessage) new IceError("ClassNotFoundError"),"неудачная попытка ответить клиенту что класс который получили не тот BaseMessage");//оповещаем клиента о том, что класс который получили не тот BaseMessage
                    return;//не позволяем программе дальше обрабатывать информацию
                }
                if(bm == null)
                {
                    System.out.println(new Date().toString() + " while" + " break");
                    break;
                }
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
                            if(Answer(c, outputStream, (BaseMessage) IceServer.StringsConfigBM,"версия клиентского приложения актуальна"))//отправляем клиенту объект с необходимой информацией
                            {
                                return;//не позволяем программе дальше обрабатывать информацию
                            }
                            System.out.println(" device ON");
                        }
                        else//а если нет
                        {
                            if(Answer(c, outputStream, (BaseMessage) new IceError("UsedOldVersion\t"+IceServer.version),"версия клиентского приложения НЕ актуальна"))//оповещаем клиента о том, что он использует устаревшую версию приложения
                            {
                                return;//не позволяем программе дальше обрабатывать информацию
                            }
                            System.out.println(new Date().toString() + " Еries to use the old version of the library");
                        }
                        continue;
                    }
                    System.out.println(new Date().toString() + " WTF O_o" + "BaseMessage not notification");
                    return;//не позволяем программе дальше обрабатывать информацию
                }
                //если вы дошли до сюда, значит вы хотите работать с данными пользователей...
                //или вы неведома зверушка
                List<String> userlist = G_S_L(IceServer.accpath, c, outputStream);//читаем лист объектов из файла с аккаунтами
                if(userlist == null)//и если чтение прошло успешно то продолжаем
                {
                    return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
                }
                if (c == login.class)//Авторизация пользователя
                {
                    System.out.println(new Date().toString() + " login");
                    user u = API.Get_user(((login) bm).get_log(), userlist); //попытка добыть объект данных пользователя по заданному логину
                    if(u != null)//если добыли
                    {
                        if(u.GetPass().equals(((login) bm).get_pass()))//если запрошеный пароль совпадает с паролем найденного пользователя
                        {
                            AuthMessaging(u, outputStream, inputStream);//переходим в обработку сообщений клиентского приложения от конкретного пользователя
                            return;
                        }
                        else
                        {
                            System.out.println(new Date().toString() + " wrong password");//логин совпал, а пароль нет
                        }
                    }
                    //если не добыли
                    System.out.println(new Date().toString() + " Auth not successful");//нужно вывести сообщение о неудаче
                    if(Answer(c, outputStream, (BaseMessage) new IceError("AuthNotSuccessful"),"неудачная попытка ответить клиенту что авторизация не удалась"))//и ответить соответственно клиенту
                    {
                        return;//не позволяем программе дальше обрабатывать информацию
                    }
                    continue;
                }
                if (c == user.class)//Добавление заявки на регистрацию
                {
                    System.out.println(new Date().toString() + " user");
                    user u = API.Get_user(((user) bm).GetMail(), userlist);//попытка добыть объект данных пользователя по заданному логину
                    if(u == null)//если не добыли (это хорошо, потому что мыло не занято)
                    {
                        List<String> toreglist = G_S_L(IceServer.toreg, c, outputStream);//переделываем список подтвержденных пользователей в список неподтвержденных, который пытаемся достать из файла
                        if(toreglist == null)//и если чтение прошло успешно то продолжаем
                        {
                            return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
                        }
                        toreglist.add(u.toString());//добавляем в список новобранца
                        //и записываем в файл
                        if(Add_String_List(toreglist, IceServer.toreg, c, outputStream))//и если запись прошла успешно то продолжаем
                        {
                            return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
                        }
                        System.out.println(new Date().toString() + " Registration successful");
                        try
                        {
                            SendEmail.sendText(((user) bm).GetMail(), "Регистрация", "Привет от ICENGO!" +"\n" +
                                    "Ваша заявка успешно добавлена и будет обработана в течении нескольких минут." +"\n" +
                                    "Спасибо."); //Запилить текст сообщения в файл
                        }
                        catch (MessagingException ex)
                        {
                            System.out.println(new Date().toString() + " WTF O_o" + " проблема с электронной почтой" +"\n" +
                                    c.toString() + "не удалось отправить письмо с результатом регистрации");
                            System.out.println(new Date().toString() + ex.toString());
                            Answer(c, outputStream, (BaseMessage) new IceError("MessagingError"),"неудачная попытка ответить клиенту что письмо отправить не удалось");//попытка ответить клиенту что письмо отправить не удалось
                            return;//не позволяем программе дальше обрабатывать информацию
                        }
                        System.out.println(new Date().toString() + " Send Registration Mail");
                        if(Answer(c, outputStream, (BaseMessage) new ping("RegistrationSuccessful"),"неудачная попытка ответить клиенту что всё прошло успешно"))//оповещаем клиента о том, что всё прошло успешно
                        {
                            return;//не позволяем программе дальше обрабатывать информацию
                        }
                        System.out.println(new Date().toString() + " Registration request send");
                    }
                    else//а если достали
                    {
                        System.out.println(new Date().toString() + " Mail is used.");
                        if(Answer(c, outputStream, (BaseMessage) new IceError("MailIsUsed"),"неудачная попытка ответить клиенту что такой электронный адресс уже используется"))//оповещаем клиента о том, что такой электронный адресс уже используется
                        {
                            return;//не позволяем программе дальше обрабатывать информацию
                        }
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
                        try
                        {
                            SendEmail.sendText(u.GetMail(), "ICENGO Пароль", "Ваш пароль - " + u.GetPass() + "\nЕсли у вас не получается войти под этим паролем, обратитесь к администратору");//значит можно взять из объекта пароль и отправить его на адрес пользователя
                        }
                        catch (MessagingException ex)
                        {
                            System.out.println(new Date().toString() + " WTF O_o" + " проблема с электронной почтой" +"\n" +
                                    c.toString() + "не удалось отправить письмо на восстановление пароля");
                            System.out.println(new Date().toString() + ex.toString());
                            Answer(c, outputStream, (BaseMessage) new IceError("MessagingError"),"неудачная попытка ответить клиенту что письмо отправить не удалось");//попытка ответить клиенту что письмо отправить не удалось
                            return;//не позволяем программе дальше обрабатывать информацию
                        }
                        System.out.println(new Date().toString() + " Password will be send");
                        if(Answer(c, outputStream, (BaseMessage) new ping("forgetOk"),"неудачная попытка ответить клиенту что всё прошло успешно"))//оповещаем клиента о том, что всё прошло успешно
                        {
                            return;//не позволяем программе дальше обрабатывать информацию
                        }
                        System.out.println(new Date().toString() + " Forget request send");
                    }
                    else//а если не достали
                    {
                        if(Answer(c, outputStream, (BaseMessage) new IceError("ForgetSoBed"),"неудачная попытка ответить клиенту что нет пользователя с таким логином"))//оповещаем клиента о том, что нет пользователя с таким логином
                        {
                            return;//не позволяем программе дальше обрабатывать информацию
                        }
                        System.out.println(new Date().toString() + " ForgetSobed");
                    }
                    continue;
                }
                System.out.println(new Date().toString() + " WTF O_o" + " IN");
                return;//не позволяем программе дальше обрабатывать информацию
            }
        System.out.println(new Date().toString() + " WTF O_o" + " OUT");
        return;//не позволяем программе дальше обрабатывать информацию
    }
    //ПОСЛЕ АВТОРИЗАЦИИ
    private void AuthMessaging(user authuser, ObjectOutputStream outputStream, ObjectInputStream inputStream)
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
        
        Itog myitog;//объект итогов пользователя
        List<BaseMessage> loglist = G_BM_L(fullname, BaseMessage.class, outputStream);//читаем лист объектов из файла логов
        if(loglist == null)//и если чтение прошло успешно то продолжаем
        {
            return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
        }
        myitog = API.Get_Itog(authuser.GetMail(), loglist);//попытка достать объект итогов пользователя
        if(myitog == null)//если объект не существует
        {
            myitog = new Itog(authuser.GetMail());//создаём его по умолчанию
            loglist.add((BaseMessage) myitog);//добавляем в лист
            //записываем лист в файл лога
            if(AddMessage(loglist, fullname, BaseMessage.class, outputStream))//и если запись прошла успешно то продолжаем
            {
                return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
            }
        }
        if(Answer(Itog.class, outputStream, (BaseMessage) myitog,"неудачная попытка отправить пользователю объект итогов из которого он может взять все необходимые данные"))//отправляем пользователю объект итогов из которого он может взять все необходимые данные
        {
            return;//не позволяем программе дальше обрабатывать информацию
        }
        System.out.println(new Date().toString() + " Itog will be send to " + authuser.GetMail());
        BaseMessage bm;
        while (true)
        {
            try
            {
                bm = (BaseMessage) inputStream.readObject();
            }
            catch (IOException ex)
            {
                System.out.println(new Date().toString() + " WTF O_o" + " проблема с чтением объекта"+ " AuthMessaging");
                System.out.println(new Date().toString() + ex.toString());
                Answer(BaseMessage.class, outputStream, (BaseMessage) new IceError("ReadObject"),"неудачная попытка ответить клиенту что проблема с чтением объекта");//оповещаем клиента о том, что проблема с чтением объекта
                return;//не позволяем программе дальше обрабатывать информацию
            }
            catch (ClassNotFoundException ex)
            {
                System.out.println(new Date().toString() + " WTF O_o" + " проблемма с классами"+ " AuthMessaging");
                System.out.println(new Date().toString() + ex.toString());
                Answer(BaseMessage.class, outputStream, (BaseMessage) new IceError("ClassNotFoundError"),"неудачная попытка ответить клиенту что класс который получили не тот BaseMessage");//оповещаем клиента о том, что класс который получили не тот BaseMessage
                return;//не позволяем программе дальше обрабатывать информацию
            }
            if(bm == null)
            {
                System.out.println(new Date().toString() + " while" + " break");
                break;
            }
            if (API.Get_BM(bm, loglist)!=null)//порверяем не записывал ли сервер объект с таким же UI
            {
                //уже есть
                if(bm.getTypeMessage()==BaseMessage.TypeMessage.add)//и лезет опять на добавление
                {
                    System.out.println(new Date().toString() + " поймали ещё одного лох-несса " + bm.toString() + 
                            " " + " Logsession " + loglist);
                    Answer(BaseMessage.class, outputStream, (BaseMessage) new IceError("loch-ness_bug"),"неудачная попытка ответить клиенту что поймали ещё одного лох-несса");//оповещаем клиента о том, что поймали ещё одного лох-несса
                    return;//не позволяем программе дальше обрабатывать информацию
                }
                //и лезет куда-то ещё 0о
                System.out.println(new Date().toString() + " что-то новенькое 0о " + bm.toString() + 
                        " " + " Logsession " + loglist);
                    Answer(BaseMessage.class, outputStream, (BaseMessage) new IceError("new_bug"),"неудачная попытка ответить клиенту что что-то новенькое 0о");//оповещаем клиента о том, что что-то новенькое 0о
                return;//не позволяем программе дальше обрабатывать информацию
            }
            //Сюда мы с Тошиком напишем реакцию сервера на каждый из классов, которые может принять сервер...
            //И будет нам счастье!
            Class c = bm.getClass();
            if (c == ping.class)//от клиента пришло сообщение о том, что клиент начал работать на определенном этапе
            {
                if(bm.getTypeMessage() == BaseMessage.TypeMessage.notification)//и это сообщение с названием магазина
                {
                    ping p = (ping) bm;
                    myitog.date_open = p.GetDate();
                    myitog.day_otw = API.weektoString(myitog.date_open.getDay());
                    myitog.nameshop = p.GetPing();
                    System.out.println(new Date().toString() + " открытие " + p.GetPing());
                    loglist = API.Set_Itog(myitog, loglist);//переписываем объект итогов внутри листа объектов лога
                    System.out.println(new Date().toString() + " Set_Itog");
                    //записываем лист в файл лога
                    if(AddMessage(loglist, fullname, c, outputStream))//и если запись прошла успешно то продолжаем
                    {
                        return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
                    }
                }
                loglist.add(bm);//просто добавляем это сообщение в лист объектов лога
                //записываем лист в файл лога
                if(AddMessage(loglist, fullname, c, outputStream))//и если запись прошла успешно то продолжаем
                {
                    return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
                }
                System.out.println(new Date().toString() + " ping " + ((ping) bm).GetPing() + " " + authuser.GetMail());
                if(Answer(Itog.class, outputStream, bm,"неудачная попытка //зеркально ответить клиенту"))//зеркально отвечаем клиенту
                {
                    return;//не позволяем программе дальше обрабатывать информацию
                }
                continue;
            }
            if (c == DataForRecord.class)//пришло сообщение содержащее данные для записи
            {
                System.out.println(new Date().toString() + " DFR " + authuser.GetMail());
                DataForRecord p = (DataForRecord) bm;
                System.out.println(new Date().toString() + "  " + Translate(p.getTypeEvent()));
                if (p.getTypeEvent() == DataForRecord.TypeEvent.open || p.getTypeEvent() == DataForRecord.TypeEvent.close)//и это касается конкретно открытия или закрытия смены 
                {
                    String pdfname;//имя для PDF отчёта
                    loglist.add(bm);//добавляем это сообщение в лист объектов лога
                    myitog = API.Calculate_Itog(myitog, authuser, loglist);//пересчитываем объект итогов
                    System.out.println(new Date().toString() + " Calculate_Itog");
                    loglist = API.Set_Itog(myitog, loglist);//переписываем объект итогов внутри листа объектов лога
                    System.out.println(new Date().toString() + " Set_Itog");
                    //записываем лист в файл лога
                    if(AddMessage(loglist, fullname, c, outputStream))//и если запись прошла успешно то продолжаем
                    {
                        return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
                    }
                    String mailtext = myitog.day_otw+"\n"+
                            "Начало рабочего дня "+myitog.date_open.getHours()+":"+CreatePDF.minutes(myitog.date_open.getMinutes()+"");
                        pdfname = myitog.nameshop + " " + filename + " " + Translate(p.getTypeEvent());//создаём имя для PDF отчёта
                    if (p.getTypeEvent() == DataForRecord.TypeEvent.open)//если было открытие
                    {
                        System.out.println(new Date().toString() + " имя для PDF отчёта " + pdfname);
                        try
                        {
                            CreatePDF._CreatePDF(IceServer.StringsConfigBM, authuser,
                                    p, myitog,
                                    pdfdir + "/" + pdfname);//запускаем метод класса CreatePDF для создания отчёта на открытие
                        }
                        catch (DocumentException ex)
                        {
                            System.out.println(new Date().toString() + " WTF O_o" + " проблема с созданием PDF"+ " " + Translate(p.getTypeEvent()));
                            System.out.println(new Date().toString() + ex.toString());
                            Answer(c, outputStream, (BaseMessage) new IceError("PDFDocumentError"),"неудачная попытка ответить клиенту что проблема с созданием PDF");//оповещаем клиента о том, что проблема с созданием PDF
                            return;//не позволяем программе дальше обрабатывать информацию
                        }
                        catch (FileNotFoundException ex)
                        {
                            System.out.println(new Date().toString() + " WTF O_o" + " проблема с чтением из файла PDF"+ " " + Translate(p.getTypeEvent()));
                            System.out.println(new Date().toString() + ex.toString());
                            Answer(c, outputStream, (BaseMessage) new IceError("PDFFileError"),"неудачная попытка ответить клиенту что проблема с чтением из файла PDF");//оповещаем клиента о том, что проблема с чтением из файла
                            return;//не позволяем программе дальше обрабатывать информацию
                        }
                    }
                    else//а если закрытие
                    {
                        DataForRecord dfropen = API.Get_DFR(DataForRecord.TypeEvent.open, loglist);//пробуем добыть данные с открытия
                        DataForRecord dfrdrug = API.Get_DFR(DataForRecord.TypeEvent.drug, loglist);//пробуем добыть данные с приходами
                        if(dfrdrug == null)
                        {
                            dfrdrug = new DataForRecord(IceServer.StringsConfigBM);
                        }
                        DataForRecord dfrsteal = API.Get_DFR(DataForRecord.TypeEvent.steal, loglist);//уходами
                        if(dfrsteal == null)
                        {
                            dfrsteal = new DataForRecord(IceServer.StringsConfigBM);
                        }
                        try
                        {
                            CreatePDF._CreatePDF(IceServer.StringsConfigBM, authuser,
                                    dfropen,
                                    dfrdrug,
                                    dfrsteal,
                                    p, myitog,
                                    pdfdir + "/" + pdfname);//запускаем метод класса CreatePDF для создания отчёта на закрытие
                        }
                        catch (DocumentException ex)
                        {
                            System.out.println(new Date().toString() + " WTF O_o" + " проблема с созданием PDF"+ " " + Translate(p.getTypeEvent()));
                            System.out.println(new Date().toString() + ex.toString());
                            Answer(c, outputStream, (BaseMessage) new IceError("PDFDocumentError"),"неудачная попытка ответить клиенту что проблема с созданием PDF");//оповещаем клиента о том, что проблема с созданием PDF
                            return;//не позволяем программе дальше обрабатывать информацию
                        }
                        catch (FileNotFoundException ex)
                        {
                            System.out.println(new Date().toString() + " WTF O_o" + " проблема с чтением из файла PDF"+ " " + Translate(p.getTypeEvent()));
                            System.out.println(new Date().toString() + ex.toString());
                            Answer(c, outputStream, (BaseMessage) new IceError("PDFFileError"),"неудачная попытка ответить клиенту что проблема с чтением из файла PDF");//оповещаем клиента о том, что проблема с чтением из файла
                            return;//не позволяем программе дальше обрабатывать информацию
                        }
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
                        try
                        {
                            SendEmail.sendPdf(
                                    mail,
                                    pdfname, //Тема сообщения
                                    mailtext,
                                    pdfdir + "/" + pdfname);
                        }
                        catch (MessagingException ex)
                        {
                            System.out.println(new Date().toString() + " WTF O_o" + " проблема с электронной почтой" +"\n" +
                                    c.toString() + "не удалось отправить письмо с результатом регистрации");
                            System.out.println(new Date().toString() + ex.toString());
                            Answer(c, outputStream, (BaseMessage) new IceError("MessagingError"),"неудачная попытка ответить клиенту что письмо отправить не удалось");//попытка ответить клиенту что письмо отправить не удалось
                            return;//не позволяем программе дальше обрабатывать информацию
                        }
                        catch (UnsupportedEncodingException ex)
                        {
                            System.out.println(new Date().toString() + " WTF O_o" + " проблема с кодировкой" +"\n" +
                                    c.toString() + "не удалось отправить письмо с результатом регистрации");
                            System.out.println(new Date().toString() + ex.toString());
                            Answer(c, outputStream, (BaseMessage) new IceError("MessagingError"),"неудачная попытка ответить клиенту что письмо отправить не удалось");//попытка ответить клиенту что письмо отправить не удалось
                            return;//не позволяем программе дальше обрабатывать информацию
                        }
                        System.out.println(new Date().toString() + "  " + pdfname + " PDF send to " + mail);
                    }
                    if(Answer(Itog.class, outputStream, (BaseMessage) myitog,"неудачная попытка отправить клиенту итоги"))//отправляем итоги клиенту
                    {
                        return;//не позволяем программе дальше обрабатывать информацию
                    }
                    System.out.println(new Date().toString() + " DFR request send" + " " + Translate(p.getTypeEvent()));
                    continue;
                }
                if (p.getTypeEvent() == DataForRecord.TypeEvent.drug || p.getTypeEvent() == DataForRecord.TypeEvent.steal)//а если всё же пришла дата по приходу или уходу
                {
                    DataForRecord tmp = API.Get_DFR(p.getTypeEvent(), loglist);//сначала пытаемся достать то, что уже было
                    if(tmp!=null)//если такое 0_0 было раньше
                    {
                        tmp.addData(p, true);//то складываем 
                        loglist = API.Set_DFR(tmp, loglist);
                    }
                    else//ну а если не было, то принятый объект теперь будет актуальным 
                    {
                        loglist.add(bm);
                    }
                    //записываем лист в файл лога
                    if(AddMessage(loglist, fullname, c, outputStream))//и если запись прошла успешно то продолжаем
                    {
                        return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
                    }
                    System.out.println(new Date().toString() + " " + "DFRrequest " + filename);
                    if(Answer(Itog.class, outputStream, (BaseMessage) myitog,"неудачная попытка отправить клиенту итоги"))//отправляем итоги клиенту
                    {
                        return;//не позволяем программе дальше обрабатывать информацию
                    }
                    System.out.println(new Date().toString() + " DFR request send" + " " + Translate(p.getTypeEvent()));
                    continue;
                }
                //а такое может случиться в случае, если мы изменили суть DataForRecord и не согласовали его работу
                System.out.println(new Date().toString() + " нужно срочно проверить работу DataForRecord на сервере и клиенте!");
                return;//не позволяем программе дальше обрабатывать информацию
            }
            if (c == DataCass.class)
            {
                System.out.println(new Date().toString() + " DataCass" + authuser.GetMail());
                System.out.println(new Date().toString() + Translate(((DataCass)bm).getTypeEvent()));
                loglist.add(bm);
                //записываем лист в файл лога
                if(AddMessage(loglist, fullname, c, outputStream))//и если запись прошла успешно то продолжаем
                {
                    return;//а если не успешно, то не позволяем программе дальше обрабатывать информацию
                }
                if(Answer(c, outputStream, (BaseMessage) new ping("DataCassOk"),"неудачная попытка ответить клиенту что всё прошло успешно"))//оповещаем клиента о том, что всё прошло успешно
                {
                    return;//не позволяем программе дальше обрабатывать информацию
                }
                System.out.println(new Date().toString() + " DataCass request send" + " " + Translate(((DataCass)bm).getTypeEvent()));
                continue;
            }
            if (c == LastMessage.class)
            {
                System.out.println(new Date().toString() + " Бабай!");
                return;
            }
            System.out.println(new Date().toString() + " WTF O_o" + " IN");
            return;//не позволяем программе дальше обрабатывать информацию
        }
        System.out.println(new Date().toString() + " WTF O_o" + " OUT");
        return;//не позволяем программе дальше обрабатывать информацию
    }
    
    //функция для получения листа объектов класса BaseMessage из файла
    //в случае если в файле листа нет, то создаётся новый лист
    //при успешном исходе возвращается лист объектов класса BaseMessage
    //в случае если произошла проблема выводится сообщение с сутью проблемы и совершается попытка ответить клиенту, о том что получить лист объектов класса BaseMessage не удалось
    //при неудачном исходе возвращается null
    private List<BaseMessage> G_BM_L(String path, Class c, ObjectOutputStream os)
    {
        List<BaseMessage> bmlist;
        try
        {
            File f = new File(path);
            if (!f.exists())
            {
                f.createNewFile();
                System.out.println(new Date().toString() + " createNewFile " + path);
            }
            bmlist = API.Get_BM_List(path); //список с данными пользователей
            if(bmlist == null)//если списка не существует
            {
                bmlist = new ArrayList();//его нужно создать
                API.AddMessage(bmlist, path);//и записать в файл
            }
            return bmlist;//всё круто
        }
        catch (IOException ex)
        {
            System.out.println(new Date().toString() + " WTF O_o" + " проблема с чтением из файла" +"\n" +
                    c.toString() + " неудачная попытка получить список объектов лога");
            System.out.println(new Date().toString() + " " + ex.toString());
            Answer(c, os, (BaseMessage) new IceError("ReadAllObjectsError"),"неудачная попытка ответить клиенту что чтение из файла не удалось");//оповещаем клиента о том, что неудачная попытка получить список объектов лога
        }
        catch (ClassNotFoundException ex)
        {
            System.out.println(new Date().toString() + " WTF O_o" + " проблема с классами" +"\n" +
                    c.toString() + " класс который достаём не тот BaseMessage");
            System.out.println(new Date().toString() + " " + ex.toString());
            Answer(c, os, (BaseMessage) new IceError("ClassNotFoundError"),"неудачная попытка ответить клиенту что класс который получили не тот BaseMessage");//оповещаем клиента о том, что класс который получили не тот BaseMessage
        }
        return null;//не позволяем программе дальше обрабатывать информацию
    }
    private List<String> G_S_L(String path, Class c, ObjectOutputStream os)
    {
        List<String> bmlist;
        try
        {
            bmlist = API.Get_String_List(path); //список с данными пользователей
            if(bmlist == null)//если списка не существует
            {
                bmlist = new ArrayList();//его нужно создать
                API.Add_String_List(bmlist, path);//и записать в файл
            }
            return bmlist;//всё круто
        }
        catch (IOException ex)
        {
            System.out.println(new Date().toString() + " WTF O_o" + " проблема с чтением из файла" +"\n" +
                    c.toString() + " неудачная попытка получить список пользователей");
            System.out.println(new Date().toString() + " " + ex.toString());
            Answer(c, os, (BaseMessage) new IceError("ReadAllObjectsError"),"неудачная попытка ответить клиенту что чтение из файла не удалось");//оповещаем клиента о том, что неудачная попытка получить список пользователей
        }
        catch (ClassNotFoundException ex)
        {
            System.out.println(new Date().toString() + " WTF O_o" + " проблема с классами" +"\n" +
                    c.toString() + " класс который достаём не тот BaseMessage");
            System.out.println(new Date().toString() + " " + ex.toString());
            Answer(c, os, (BaseMessage) new IceError("ClassNotFoundError"),"неудачная попытка ответить клиенту что класс который получили не тот BaseMessage");//оповещаем клиента о том, что класс который получили не тот BaseMessage
        }
        return null;//не позволяем программе дальше обрабатывать информацию
    }
    //функция для записи листа объектов класса BaseMessage в файл
    //при успешном исходе возвращается false
    //в случае если произошла проблема выводится сообщение с сутью проблемы и совершается попытка ответить клиенту, о том что записать лист объектов класса BaseMessage в фал не удалось
    //при неудачном исходе возвращается true
    private boolean AddMessage(List<BaseMessage> bmlist, String path, Class c, ObjectOutputStream os)
    {
        try
        {
            API.AddMessage(bmlist, path);//и записать в файл
            return false;
        }
        catch (IOException ex)
        {
            System.out.println(new Date().toString() + " WTF O_o" + " проблема с записью в файл" +"\n" +
                    c.toString() + "неудачная попытка записать список объектов лога");
            System.out.println(new Date().toString() + ex.toString());
            Answer(c, os, (BaseMessage) new IceError("WriteAllObjectsError"),"попытка ответить клиенту что запись в файл не удалось");//оповещаем клиента о том, что неудачная попытка записать список объектов лога
        }
        catch (ClassNotFoundException ex)
        {
            System.out.println(new Date().toString() + " WTF O_o" + " проблема с классами" +"\n" +
                    c.toString() + "класс который получили не тот BaseMessage");
            System.out.println(new Date().toString() + ex.toString());
            Answer(c, os, (BaseMessage) new IceError("ClassNotFoundError"),"неудачная попытка ответить клиенту что класс который получили не тот BaseMessage");//оповещаем клиента о том, что класс который получили не тот BaseMessage
        }
        return true;//не позволяем программе дальше обрабатывать информацию
    }
    private boolean Add_String_List(List<String> bmlist, String path, Class c, ObjectOutputStream os)
    {
        try
        {
            API.Add_String_List(bmlist, path);//и записать в файл
            return false;
        }
        catch (IOException ex)
        {
            System.out.println(new Date().toString() + " WTF O_o" + " проблема с записью в файл для регистрации" +"\n" +
                    c.toString() + "неудачная попытка записать список регистрирующихся");
            System.out.println(new Date().toString() + ex.toString());
            Answer(c, os, (BaseMessage) new IceError("WriteAllObjectsError"),"попытка ответить клиенту что запись в файл не удалось");//оповещаем клиента о том, что неудачная попытка записать список объектов лога
        }
        catch (ClassNotFoundException ex)
        {
            System.out.println(new Date().toString() + " WTF O_o" + " проблема с классами" +"\n" +
                    c.toString() + "класс который получили не тот String");
            System.out.println(new Date().toString() + ex.toString());
            Answer(c, os, (BaseMessage) new IceError("ClassNotFoundError"),"неудачная попытка ответить клиенту что класс который получили не тот String");//оповещаем клиента о том, что класс который получили не тот BaseMessage
        }
        return true;//не позволяем программе дальше обрабатывать информацию
    }
    //функция для попытки ответить клиенту
    //при успешном исходе возвращается false
    //в случае если произошла проблема выводится сообщение с сутью проблемы
    //при неудачном исходе возвращается true
    private boolean Answer(Class c, ObjectOutputStream os, BaseMessage bm, String submessage)
    {
        try
        {
            os.writeObject(bm);//и ответить соответственно клиенту
            return false;
        }
        catch (IOException ex)
        {
            System.out.println(new Date().toString() + " WTF O_o" + " проблема с записью объекта" +"\n" +
                    c.toString()+ " - "+submessage);
            System.out.println(new Date().toString() + ex.toString());
        }
        return true;//не позволяем программе дальше обрабатывать информацию
    }
    
    //создание пути к директории лога
    private String CreateLogDirName(user us, String logDirPath)
    {
        Date date = us.GetDate();

        int year = date.getYear() + 1900;
        int mounth = date.getMonth() + 1;
        int day = date.getDate();

        String path = logDirPath + year + "/" + mounth + "/" + day;
        if (us.GetSuper())
        {
            path += "/" + "supers" + "/" + us.GetMail();
        }
        return path;
    }
    //создание имени лога
    private String FileName(user us)
    {
        Date date = us.GetDate();

        int year = date.getYear() + 1900;
        int mounth = date.getMonth() + 1;
        int day = date.getDate();

        String FileName = us.GetSurname() + " " + day + "." + mounth + "." + year;

        if (us.GetSuper())
        {
            String path = CreateLogDirName(us, IceServer.logpath);
            int num = new File(path).list().length -3;
            if(num < 0)
            {
                num = 0;
            }
            FileName += " " + num;
        }
        return FileName;
    }

    //перевод из перечеслений DataForRecord.TypeEvent в String на русском
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
        if (typeEvent == DataForRecord.TypeEvent.drug)
        {
            return "Приход";
        }
        if (typeEvent == DataForRecord.TypeEvent.steal)
        {
            return "Уход";
        }
        return "ERROR";
    }
    //перевод из перечеслений DataCass.TypeEvent в String на русском
    private static String Translate(DataCass.TypeEvent typeEvent)
    {
        if (typeEvent == DataCass.TypeEvent.cass)
        {
            return "Аванс";
        }
        if (typeEvent == DataCass.TypeEvent.inkasator)
        {
            return "Инкасация";
        }
        if (typeEvent == DataCass.TypeEvent.promoter)
        {
            return "Промоутер";
        }
        return "ERROR";
    }
}