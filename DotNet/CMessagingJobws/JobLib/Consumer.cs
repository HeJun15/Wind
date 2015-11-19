using System;
using System.Collections.Generic;
using System.Configuration;
using System.Threading;
using Arch.CMessaging.Client.API;
using Arch.CMessaging.Client.Impl.Consumer;
using Arch.CMessaging.Core.Content;
using Freeway.Logging;

namespace CMessagingJobws.JobLib
{
    public class Consumer
    {
        private static readonly  Consumer instance = new Consumer();
        public static Consumer Instance
        {
            get { return instance; }
        }
        ILog log = LogManager.GetLogger("cmessaging");

        private int isSington=0;
        public bool ConsumeAsync(Action<IMessage> action)
        {
            string topic = "";
            string exchange = "";
            string identifier = "";
            try
            {
                if (Interlocked.CompareExchange(ref isSington, 1, 0) != 0) return true;//保证单例，已运行的不再运行

                //读取配置文件
                topic = ConfigurationManager.AppSettings["topic"];
                exchange = ConfigurationManager.AppSettings["exchange"];
                identifier = ConfigurationManager.AppSettings["identifier"];

                var consumer  = ConsumerFactory.Instance.CreateAsTopic(topic, exchange, identifier);
                //注册事件
                consumer.Callback += (c, e) =>
                {
                    try
                    {
                        //执行业务处理
                        action(e.Message);
                    }
                    catch (Exception ex)
                    {
                        //标记为Nack
                        e.Message.Acks = AckMode.Nack;
                        //记录异常日志
                        log.Error("CMessagingJobws.Consumer.ConsumeAsync", ex,
                                  new Dictionary<string, string>()
                                      {{"topic", topic}, {"exchange", exchange}, {"identifier", identifier}});
                    }
                };
                //开启消费
                consumer.ConsumeAsync(5, false);
                return true;
            }
            catch(Exception ex)
            {
                //记录异常日志
                log.Error("CMessagingJobws.Consumer.ConsumeAsync", ex,
                          new Dictionary<string, string>()
                              {{"topic", topic}, {"exchange", exchange}, {"identifier", identifier}});
                
                Interlocked.Exchange(ref isSington, 0);
                return false;
            }
        }
    }
}