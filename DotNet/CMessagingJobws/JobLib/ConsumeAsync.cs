using System;
using Arch.CMessaging.Client.API;
using Arch.JobAgent.API;
using Arch.JobAgent.API.Entity;
using Freeway.Logging;

namespace CMessagingJobws.JobLib
{
    /// <summary>
    /// CMesssaging异步
    /// </summary>
    public class ConsumeAsync : JobBase
    {
        ILog log = LogManager.GetLogger("cmessaging");
        protected override void OnStart(string param)
        {
            try
            {
                //执行业务处理
                var result = Consumer.Instance.ConsumeAsync(ConsumerCallback);
                if(!result)
                {
                    //失败处理

                }

                //标识job运行结果
                this.JobResult = result ? JobResult.Success : JobResult.Exception;
            }
            catch (Exception ex)
            {
                //记录异常日志
                log.Error("CMessagingJobws.Consumer.OnStart", ex);

                //标识job执行异常
                this.JobResult = JobResult.Exception;
            }
           
            //标识job已经停止
            this.JobStatus = JobStatus.Stoped;
        }

        void ConsumerCallback(IMessage message)
        {
            //执行业务处理
            
            //message.HeaderProperties 消息头，用于对消息本身进行描述。
            //message.HeaderProperties.Subject 消息主题 
            //message.HeaderProperties.CorrelationID 这个属性调用者可以不用指定，消息生产者会对这个属性进行管理。如果调用者需要将消息和前一次请求串联，可以通过这个ID关联。

            //获取文本消息 var messageContent = message.GetText();
            //获取消息对象 var object = message.GetObject<T>();
        }
    }
}