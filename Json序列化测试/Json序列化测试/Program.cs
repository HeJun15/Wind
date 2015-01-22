using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using fastJSON;
using Newtonsoft.Json;
using System.Diagnostics;
using System.Xml.Serialization;
using System.Xml;
using System.Runtime.Serialization.Formatters.Binary;
namespace JsonTest
{
    class Program
    {
        static int times = 10000;
        static int lstcount = 20;
        static void Main(string[] args)
        {
            StreamWriter sp = new StreamWriter(@"d:\Users\hejun\Desktop\test_" + times + ".js", false, System.Text.UTF8Encoding.UTF8);
            Console.SetOut(sp);

            //Console.WriteLine("大对象测试");
            //bigdto_test();
            Console.WriteLine("大对象列表测试-列表元素个数：" + lstcount);
            bigdto_list_test();

            //Console.WriteLine();
            //Console.WriteLine();

            //Console.WriteLine("简单对象测试");
            //simpledto_test();
            //Console.WriteLine("简单对象列表测试-列表元素个数：" + lstcount);
            //simpledto_list_test();



            //List<SimpleClass> lst_simpledto = new List<SimpleClass>();
            //for (int i = 0; i < lstcount; i++)
            //{
            //    lst_simpledto.Add(CreateSimpleObject());
            //}
            //Stopwatch sw = new Stopwatch();
            //sw.Start();
            //string s1 = "";
            //s1 = JSON.ToJSON(lst_simpledto);
            //sw.Stop();
            //Console.WriteLine("耗时" + sw.ElapsedMilliseconds);
            //Console.WriteLine("序列化结果：" + s1);
            //Console.WriteLine("长度：" + s1.Length);
            //Console.WriteLine();


            //ListSimpleClass lst = new ListSimpleClass() { lst = lst_simpledto };
            //sw.Start();
            //s1 = "";
            //s1 = JSON.ToJSON(lst);
            //sw.Stop();

            //Console.WriteLine("耗时" + sw.ElapsedMilliseconds);
            //Console.WriteLine("序列化结果：" + s1);
            //Console.WriteLine("长度：" + s1.Length);


            sp.Close();

        }

        class ListSimpleClass
        {
            public List<SimpleClass> lst { set; get; }
        }
        static private void bigdto_test()
        {
            #region bigdto
            var bigdto = CreateBigObject();
            #region FastJson 2.1版本序列化
            Stopwatch sw = new Stopwatch();
            sw.Start();
            string s1 = "";
            for (int i = 0; i < times; i++)
                s1 = JSON.ToJSON(bigdto);
            sw.Stop();
            Console.WriteLine("FastJson 2.1版本序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s1);
            Console.WriteLine("长度：" + s1.Length);
            Console.WriteLine();
            #endregion

            #region Json.net 6.0版本序列化
            sw.Restart();
            string s2 = "";
            for (int i = 0; i < times; i++)
                s2 = JsonConvert.SerializeObject(bigdto);
            sw.Stop();
            Console.WriteLine("Json.net 6.0版本序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s2);
            Console.WriteLine("长度：" + s2.Length);
            Console.WriteLine();
            #endregion

            #region CRedis自带的序列化
            sw.Restart();
            string s3 = "";
            for (int i = 0; i < times; i++)
                s3 = CRedis.Third.Text.TypeSerializer.SerializeToString(bigdto);
            sw.Stop();
            Console.WriteLine("CRedis自带的序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s3);
            Console.WriteLine("长度：" + s3.Length);
            Console.WriteLine();
            #endregion

            #region 微软自带的XML序列化
            sw.Restart();
            string s4 = "";
            for (int i = 0; i < times; i++)
                s4 = ToXML<ScenicSpotListItemDTO>(bigdto);
            sw.Stop();
            Console.WriteLine("微软自带的XML序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s4);
            Console.WriteLine("长度：" + s4.Length);
            Console.WriteLine();
            #endregion

            #region CserviceStack自带的序列化(ToJson)
            sw.Restart();
            string s5 = "";
            for (int i = 0; i < times; i++)
                s5 = ServiceStackToJsonString(bigdto);
            sw.Stop();
            Console.WriteLine("CserviceStack自带的序列化(ToJson)(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s5);
            Console.WriteLine("长度：" + s5.Length);
            Console.WriteLine();
            #endregion


            #region FastJson 2.1版本反序列化
            sw.Restart();
            ScenicSpotListItemDTO dto1 = null;
            for (int i = 0; i < times; i++)
                dto1 = JSON.ToObject<ScenicSpotListItemDTO>(s1);
            sw.Stop();
            Console.WriteLine("FastJson 2.1版本反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region Json.net 6.0版本反序列化
            sw.Restart();
            ScenicSpotListItemDTO dto2 = null;
            for (int i = 0; i < times; i++)
                dto2 = JsonConvert.DeserializeObject<ScenicSpotListItemDTO>(s2);
            sw.Stop();
            Console.WriteLine("Json.net 6.0版本反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region CRedis自带的反序列化
            sw.Restart();
            ScenicSpotListItemDTO dto3 = null;
            for (int i = 0; i < times; i++)
                dto3 = CRedis.Third.Text.TypeSerializer.DeserializeFromString<ScenicSpotListItemDTO>(s3);
            sw.Stop();
            Console.WriteLine("CRedis自带的反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region 微软自带的XML反序列化
            sw.Restart();
            ScenicSpotListItemDTO dto4 = null;
            for (int i = 0; i < times; i++)
                dto4 = FromXML<ScenicSpotListItemDTO>(s4);
            sw.Stop();
            Console.WriteLine("微软自带的XML反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region CserviceStack自带的反序列化(FromJson)
            sw.Restart();
            ScenicSpotListItemDTO dto5 = null;
            for (int i = 0; i < times; i++)
                dto5 = ServiceStackFromJsonString<ScenicSpotListItemDTO>(s5);
            sw.Stop();
            Console.WriteLine("CserviceStack自带的序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion
            #endregion bigdto
        }

        static private void bigdto_list_test()
        {

            #region lst_bigdto
            List<ScenicSpotListItemDTO> lst_bigdto = new List<ScenicSpotListItemDTO>();
            for (int i = 0; i < lstcount; i++)
            {
                lst_bigdto.Add(CreateBigObject());
            }

            #region FastJson 2.1版本序列化
            Stopwatch sw = new Stopwatch();
            sw.Start();
            string s1 = "";
            for (int i = 0; i < times; i++)
                s1 = JSON.ToJSON(lst_bigdto);
            sw.Stop();
            Console.WriteLine("FastJson 2.1版本序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s1);
            Console.WriteLine("长度：" + s1.Length);
            Console.WriteLine();
            #endregion

            #region Json.net 6.0版本序列化
            sw.Restart();
            string s2 = "";
            for (int i = 0; i < times; i++)
                s2 = JsonConvert.SerializeObject(lst_bigdto);
            sw.Stop();
            Console.WriteLine("Json.net 6.0版本序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s2);
            Console.WriteLine("长度：" + s2.Length);
            Console.WriteLine();
            #endregion

            #region CRedis自带的序列化
            sw.Restart();
            string s3 = "";
            for (int i = 0; i < times; i++)
                s3 = CRedis.Third.Text.TypeSerializer.SerializeToString(lst_bigdto);
            sw.Stop();
            Console.WriteLine("CRedis自带的序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s3);
            Console.WriteLine("长度：" + s3.Length);
            Console.WriteLine();
            #endregion

            #region 微软自带的XML序列化
            sw.Restart();
            string s4 = "";
            for (int i = 0; i < times; i++)
                s4 = ToXML<List<ScenicSpotListItemDTO>>(lst_bigdto);
            sw.Stop();
            Console.WriteLine("微软自带的XML序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s4);
            Console.WriteLine("长度：" + s4.Length);
            Console.WriteLine();
            #endregion

            #region CserviceStack自带的序列化(ToJson)
            sw.Restart();
            string s5 = "";
            for (int i = 0; i < times; i++)
                s5 = ServiceStackToJsonString(lst_bigdto);
            sw.Stop();
            Console.WriteLine("CserviceStack自带的序列化(ToJson)(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s5);
            Console.WriteLine("长度：" + s5.Length);
            Console.WriteLine();
            #endregion

            #region 微软自带的二进制序列化
            sw.Restart();
            byte[] s6 = null;
            for (int i = 0; i < times; i++)
                s6 = ToByte(lst_bigdto);
            sw.Stop();
            Console.WriteLine("微软自带的二进制序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s6);
            Console.WriteLine("长度：" + s6.Length);
            Console.WriteLine();
            #endregion

            #region FastJson 2.1版本反序列化
            sw.Restart();
            List<ScenicSpotListItemDTO> dto1 = null;
            for (int i = 0; i < times; i++)
                dto1 = JSON.ToObject<List<ScenicSpotListItemDTO>>(s1);
            sw.Stop();
            Console.WriteLine("FastJson 2.1版本反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region Json.net 6.0版本反序列化
            sw.Restart();
            List<ScenicSpotListItemDTO> dto2 = null;
            for (int i = 0; i < times; i++)
                dto2 = JsonConvert.DeserializeObject<List<ScenicSpotListItemDTO>>(s2);
            sw.Stop();
            Console.WriteLine("Json.net 6.0版本反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region CRedis自带的反序列化
            sw.Restart();
            List<ScenicSpotListItemDTO> dto3 = null;
            for (int i = 0; i < times; i++)
                dto3 = CRedis.Third.Text.TypeSerializer.DeserializeFromString<List<ScenicSpotListItemDTO>>(s3);
            sw.Stop();
            Console.WriteLine("CRedis自带的反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region 微软自带的XML反序列化
            sw.Restart();
            List<ScenicSpotListItemDTO> dto4 = null;
            for (int i = 0; i < times; i++)
                dto4 = FromXML<List<ScenicSpotListItemDTO>>(s4);
            sw.Stop();
            Console.WriteLine("微软自带的XML反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region CserviceStack自带的反序列化(FromJson)
            sw.Restart();
            List<ScenicSpotListItemDTO> dto5 = null;
            for (int i = 0; i < times; i++)
                dto5 = ServiceStackFromJsonString<List<ScenicSpotListItemDTO>>(s5);
            sw.Stop();
            Console.WriteLine("CserviceStack自带的序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region 微软自带的二进制反序列化
            sw.Restart();
            List<ScenicSpotListItemDTO> dto6 = null;
            for (int i = 0; i < times; i++)
                dto6 = FromByte<List<ScenicSpotListItemDTO>>(s6);
            sw.Stop();
            Console.WriteLine("微软自带的二进制反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion


            #endregion lst_bigdto
        }

        static private void simpledto_test()
        {
            #region simpledto
            var simpledto = CreateSimpleObject();
            #region FastJson 2.1版本序列化
            Stopwatch sw = new Stopwatch();
            sw.Start();
            string s1 = "";
            for (int i = 0; i < times; i++)
                s1 = JSON.ToJSON(simpledto);
            sw.Stop();
            Console.WriteLine("FastJson 2.1版本序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s1);
            Console.WriteLine("长度：" + s1.Length);
            Console.WriteLine();
            #endregion

            #region Json.net 6.0版本序列化
            sw.Restart();
            string s2 = "";
            for (int i = 0; i < times; i++)
                s2 = JsonConvert.SerializeObject(simpledto);
            sw.Stop();
            Console.WriteLine("Json.net 6.0版本序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s2);
            Console.WriteLine("长度：" + s2.Length);
            Console.WriteLine();
            #endregion

            #region CRedis自带的序列化
            sw.Restart();
            string s3 = "";
            for (int i = 0; i < times; i++)
                s3 = CRedis.Third.Text.TypeSerializer.SerializeToString(simpledto);
            sw.Stop();
            Console.WriteLine("CRedis自带的序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s3);
            Console.WriteLine("长度：" + s3.Length);
            Console.WriteLine();
            #endregion

            #region 微软自带的XML序列化
            sw.Restart();
            string s4 = "";
            for (int i = 0; i < times; i++)
                s4 = ToXML<SimpleClass>(simpledto);
            sw.Stop();
            Console.WriteLine("微软自带的XML序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s4);
            Console.WriteLine("长度：" + s4.Length);
            Console.WriteLine();
            #endregion

            #region CserviceStack自带的序列化(ToJson)
            sw.Restart();
            string s5 = "";
            for (int i = 0; i < times; i++)
                s5 = ServiceStackToJsonString(simpledto);
            sw.Stop();
            Console.WriteLine("CserviceStack自带的序列化(ToJson)(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s5);
            Console.WriteLine("长度：" + s5.Length);
            Console.WriteLine();
            #endregion

            #region 微软自带的二进制序列化
            sw.Restart();
            byte[] s6 = null;
            for (int i = 0; i < times; i++)
                s6 = ToByte(simpledto);
            sw.Stop();
            Console.WriteLine("微软自带的二进制序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s6);
            Console.WriteLine("长度：" + s6.Length);
            Console.WriteLine();
            #endregion


            #region FastJson 2.1版本反序列化
            sw.Restart();
            SimpleClass dto1 = null;
            for (int i = 0; i < times; i++)
                dto1 = JSON.ToObject<SimpleClass>(s1);
            sw.Stop();
            Console.WriteLine("FastJson 2.1版本反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region Json.net 6.0版本反序列化
            sw.Restart();
            SimpleClass dto2 = null;
            for (int i = 0; i < times; i++)
                dto2 = JsonConvert.DeserializeObject<SimpleClass>(s2);
            sw.Stop();
            Console.WriteLine("Json.net 6.0版本反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region CRedis自带的反序列化
            sw.Restart();
            SimpleClass dto3 = null;
            for (int i = 0; i < times; i++)
                dto3 = CRedis.Third.Text.TypeSerializer.DeserializeFromString<SimpleClass>(s3);
            sw.Stop();
            Console.WriteLine("CRedis自带的反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region 微软自带的XML反序列化
            sw.Restart();
            SimpleClass dto4 = null;
            for (int i = 0; i < times; i++)
                dto4 = FromXML<SimpleClass>(s4);
            sw.Stop();
            Console.WriteLine("微软自带的XML反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region CserviceStack自带的反序列化(FromJson)
            sw.Restart();
            SimpleClass dto5 = null;
            for (int i = 0; i < times; i++)
                dto5 = ServiceStackFromJsonString<SimpleClass>(s5);
            sw.Stop();
            Console.WriteLine("CserviceStack自带的反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region 微软自带的二进制反序列化
            sw.Restart();
            SimpleClass dto6 = null;
            for (int i = 0; i < times; i++)
                dto6 = FromByte<SimpleClass>(s6);
            sw.Stop();
            Console.WriteLine("微软自带的二进制反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #endregion simpledto
        }

        static private void simpledto_list_test()
        {

            #region lst_simpledto
            List<SimpleClass> lst_simpledto = new List<SimpleClass>();
            for (int i = 0; i < lstcount; i++)
            {
                lst_simpledto.Add(CreateSimpleObject());
            }

            #region FastJson 2.1版本序列化
            Stopwatch sw = new Stopwatch();
            sw.Start();
            string s1 = "";
            for (int i = 0; i < times; i++)
                s1 = JSON.ToJSON(lst_simpledto);
            sw.Stop();
            Console.WriteLine("FastJson 2.1版本序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s1);
            Console.WriteLine("长度：" + s1.Length);
            Console.WriteLine();
            #endregion

            #region Json.net 6.0版本序列化
            sw.Restart();
            string s2 = "";
            for (int i = 0; i < times; i++)
                s2 = JsonConvert.SerializeObject(lst_simpledto);
            sw.Stop();
            Console.WriteLine("Json.net 6.0版本序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s2);
            Console.WriteLine("长度：" + s2.Length);
            Console.WriteLine();
            #endregion

            #region CRedis自带的序列化
            sw.Restart();
            string s3 = "";
            for (int i = 0; i < times; i++)
                s3 = CRedis.Third.Text.TypeSerializer.SerializeToString(lst_simpledto);
            sw.Stop();
            Console.WriteLine("CRedis自带的序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s3);
            Console.WriteLine("长度：" + s3.Length);
            Console.WriteLine();
            #endregion

            #region 微软自带的XML序列化
            sw.Restart();
            string s4 = "";
            for (int i = 0; i < times; i++)
                s4 = ToXML<List<SimpleClass>>(lst_simpledto);
            sw.Stop();
            Console.WriteLine("微软自带的XML序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s4);
            Console.WriteLine("长度：" + s4.Length);
            Console.WriteLine();
            #endregion

            #region CserviceStack自带的序列化(ToJson)
            sw.Restart();
            string s5 = "";
            for (int i = 0; i < times; i++)
                s5 = ServiceStackToJsonString(lst_simpledto);
            sw.Stop();
            Console.WriteLine("CserviceStack自带的序列化(ToJson)(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s5);
            Console.WriteLine("长度：" + s5.Length);
            Console.WriteLine();
            #endregion

            #region 微软自带的二进制序列化
            sw.Restart();
            byte[] s6 = null;
            for (int i = 0; i < times; i++)
                s6 = ToByte(lst_simpledto);
            sw.Stop();
            Console.WriteLine("微软自带的二进制序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine("序列化结果：" + s6);
            Console.WriteLine("长度：" + s6.Length);
            Console.WriteLine();
            #endregion

            #region FastJson 2.1版本反序列化
            sw.Restart();
            List<SimpleClass> dto1 = null;
            for (int i = 0; i < times; i++)
                dto1 = JSON.ToObject<List<SimpleClass>>(s1);
            sw.Stop();
            Console.WriteLine("FastJson 2.1版本反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region Json.net 6.0版本反序列化
            sw.Restart();
            List<SimpleClass> dto2 = null;
            for (int i = 0; i < times; i++)
                dto2 = JsonConvert.DeserializeObject<List<SimpleClass>>(s2);
            sw.Stop();
            Console.WriteLine("Json.net 6.0版本反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region CRedis自带的反序列化
            sw.Restart();
            List<SimpleClass> dto3 = null;
            for (int i = 0; i < times; i++)
                dto3 = CRedis.Third.Text.TypeSerializer.DeserializeFromString<List<SimpleClass>>(s3);
            sw.Stop();
            Console.WriteLine("CRedis自带的反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region 微软自带的XML反序列化
            sw.Restart();
            List<SimpleClass> dto4 = null;
            for (int i = 0; i < times; i++)
                dto4 = FromXML<List<SimpleClass>>(s4);
            sw.Stop();
            Console.WriteLine("微软自带的XML反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region CserviceStack自带的反序列化(FromJson)
            sw.Restart();
            List<SimpleClass> dto5 = null;
            for (int i = 0; i < times; i++)
                dto5 = ServiceStackFromJsonString<List<SimpleClass>>(s5);
            sw.Stop();
            Console.WriteLine("CserviceStack自带的序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #region 微软自带的二进制反序列化
            sw.Restart();
            List<SimpleClass> dto6 = null;
            for (int i = 0; i < times; i++)
                dto6 = FromByte<List<SimpleClass>>(s6);
            sw.Stop();
            Console.WriteLine("微软自带的二进制反序列化(循环了" + times + "次) ：耗时" + sw.ElapsedMilliseconds);
            Console.WriteLine();
            #endregion

            #endregion lst_simpledto
        }


        static private ScenicSpotListItemDTO CreateBigObject()
        {
            var _ChannelDiscountList = new List<ChannelDiscountDTO>() { 
            new ChannelDiscountDTO(){
                      CashBackAmount=1000,
                        ChannelDiscountId=9922,
                        Discount=3001,
                        DistributionChannelId=992,
                        OptionId=4
                },
                new ChannelDiscountDTO(){
                      CashBackAmount=5000,
                        ChannelDiscountId=6576,
                        Discount=8656,
                        DistributionChannelId=1343,
                        OptionId=54
                }
            };
            var _ResourceListItemList = new List<ResourceListItemDTO>() { 
                new ResourceListItemDTO(){
                    ID = 1234567,
                    Name = "Name########",
                    MarketPrice = 8765,
                    Price = 7654,
                    IsReturnCash = false,
                    ReturnCashAmount = 654,
                    PeopleGroup = 543,
                    TicketGroupAttributes = null,
                    TicketType = 432,
                    IsHighRisk = true,
                    ExchangeMode = "1",
                    PayMode = "P",
                    CategoryID = 5411,
                    UnitQuantity = 233,
                    SaleTag = "yingxiaobiaoqian",
                    LimitSaleMsg = "xiangou",
                    IsPriceFree = false,
                    RefundType = 999,
                    IsBookingLimit = true,
                    ChannelDiscountList = _ChannelDiscountList,
                    IsPreferential = false,
                    IsSaleAlone = false
                } 
            };
            var _TicketGroupAttributes = new List<TicketAttributeGroupDTO>() {
            new TicketAttributeGroupDTO(){
                Key="mykey",
                Name="myname",
                TicketAttributes=new List<TicketAttributeDTO>(){new TicketAttributeDTO(){Key="kkk",Name="NNNNNNN",Value="vvv"}},
                }
            };
            var _PromotionInfoList = new List<ProductPromotionInfoDTO>() { 
                new ProductPromotionInfoDTO()
                {
                DeductionAmount=100,
                DeductionType=92,
                DisplayName="dndnddndn",
                PromotionID=75757
                }
            };
            var _ProductListItemList = new List<ProductListItemDTO>() {
                new ProductListItemDTO(){
                    ID = 0,
                    Name = "Name009",
                    WorkEndTime = "2000-01-01",
                    PayMode = "P",
                    ResourceListItemList = _ResourceListItemList,
                    MarketPrice = 1222,
                    Price = 4440,
                    IsReturnCash = true,
                    ReturnCashAmount = 446660,
                    TicketGroupAttributes = _TicketGroupAttributes,
                    PromotionInfoList = _PromotionInfoList,
                    IsMaster = true,
                    CanBookingFirstDate = DateTime.Now,
                    AdvanceBookingDays = 6780,
                    AdvanceBookingTime = "3333",
                    CategoryID = 89080
                }
                };

            ScenicSpotListItemDTO dto = new ScenicSpotListItemDTO()
            {
                ID = 9999,
                Name = Guid.NewGuid().ToString(),
                Star = 8888,
                Address = "Address_Test",
                DistrictID = 7777,
                DistrictName = "DistrictName_Test",
                ProvinceID = 6666,
                ProvinceName = "ProvinceName11223",
                CountryID = 55555,
                CountryName = "CountryName8888",
                Activity = "20000",
                ProductManagerRecommand = "ProductManagerRecommand4444444",
                CommentGrade = 3333,
                CommentUserCount = 22222,
                OrderCount = 11111,
                ProductListItemList = _ProductListItemList,
                CoverImageUrl = "http://CoverImageUrl.com",
                CoverImageId = 12,
                CoverSmallImageUrl = "http://CoverSmallImageUrl.com",
                CoverImageBaseUrl = "http://CoverImageBaseUrl.com1818181811",
                MarketPrice = 3456,
                Price = 45,
                IsReturnCash = false,
                ReturnCashAmount = -100,
                TicketGroupAttributes = null,
                Image = "Image105",
                Distance = 12.822m,
                SaleTag = "营销标签",
                LimitSaleMsg = "限购",
                ResourceReturnCash = null,
                Url = "http://URLURL.com",
            };

            return dto;
        }
        static private SimpleClass CreateSimpleObject()
        {
            SimpleClass s = new SimpleClass()
            {
                Age = 100,
                Name = "Myname",
                No = Guid.NewGuid().ToString(),
            };
            return s;
        }

        static private void WriteFile(string filename, string content)
        {
            using (StreamWriter sw = new StreamWriter(filename, false, System.Text.UTF8Encoding.UTF8))
                sw.WriteLine(content);
        }

        static private string ToXML<T>(T obj)
        {
            string result = "";
            try
            {
                using (MemoryStream ms = new MemoryStream())
                {
                    new XmlSerializer(typeof(T)).Serialize(ms, obj);
                    byte[] arr = ms.ToArray();
                    result = Encoding.UTF8.GetString(arr, 0, arr.Length);
                }
            }
            catch (Exception ex)
            {
                result = "";
            }
            return result;
        }
        static private T FromXML<T>(string xmlString)
        {
            using (MemoryStream memStream = new MemoryStream(Encoding.Unicode.GetBytes(xmlString)))
            {
                System.Xml.Serialization.XmlSerializer serializer = new System.Xml.Serialization.XmlSerializer(typeof(T));
                return (T)serializer.Deserialize(memStream);
            }
        }
        static private byte[] ToByte(object obj)
        {
            byte[] arr = null;
            try
            {
                using (MemoryStream ms = new MemoryStream())
                {
                    BinaryFormatter formatter = new BinaryFormatter();
                    formatter.Serialize(ms, obj);
                    arr = ms.ToArray();
                }
            }
            catch (Exception ex)
            {
                arr = null;
            }
            return arr;
        }
        static private T FromByte<T>(byte[] b)
        {
            T t = default(T);
            try
            {
                using (MemoryStream ms = new MemoryStream(b))
                {
                    BinaryFormatter formatter = new BinaryFormatter();
                    t = (T)formatter.Deserialize(ms);

                }
            }
            catch (Exception ex)
            {
                t = default(T);
            }
            return t;
        }



        static private string ServiceStackToJsonString(object obj)
        {
            string result = "";
            using (MemoryStream ms = new MemoryStream())
            {
                CServiceStack.Common.Utils.GeneralSerializer.Serialize(obj, ms, CServiceStack.Common.Utils.DataFormat.JSON);
                byte[] arr = ms.ToArray();
                result = Encoding.UTF8.GetString(arr, 0, arr.Length);
            }
            return result;
        }

        static private T ServiceStackFromJsonString<T>(string jsonstring)
        {
            using (MemoryStream memStream = new MemoryStream(Encoding.UTF8.GetBytes(jsonstring)))
            {
                return CServiceStack.Common.Utils.GeneralSerializer.Deserialize<T>(memStream, CServiceStack.Common.Utils.DataFormat.JSON);
            }
        }

    }
}
