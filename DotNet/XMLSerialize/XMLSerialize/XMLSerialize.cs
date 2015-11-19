using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Xml.Serialization;
using System.IO;

namespace XMLSerialize
{
    class XMLSerialize
    {
        static void Main(string[] args)
        {
            int i = 10;

            //声明Xml序列化对象实例serializer
            XmlSerializer serializer = new XmlSerializer(typeof(int));

            //执行序列化并将序列化结果输出到控制台
            serializer.Serialize(Console.Out, i);


            using (StringReader rdr = new StringReader(@"<?xml version=""1.0"" encoding=""gb2312""?><int>15</int>"))
            {
                //声明序列化对象实例serializer
                XmlSerializer newserializer = new XmlSerializer(typeof(int));

                //反序列化，并将反序列化结果值赋给变量i
                int j = (int)newserializer.Deserialize(rdr);

                //输出反序列化结果
                Console.WriteLine();
                Console.WriteLine();
                Console.WriteLine();
                Console.WriteLine("j = " + j);


                //声明一个猫咪对象
                var cWhite = new Cat { Color = "White", Speed = 10, Saying = "White or black,  so long as the cat can catch mice,  it is a good cat" };
                var cBlack = new Cat { Color = "Black", Speed = 10, Saying = "White or black,  so long as the cat can catch mice,  it is a good cat" };

                CatCollection cc = new CatCollection { Cats = new Cat[] { cWhite, cBlack } };

                //序列化这个对象
                XmlSerializer new2serializer = new XmlSerializer(typeof(CatCollection));

                //将对象序列化输出到控制台
                Console.WriteLine();
                Console.WriteLine();
                Console.WriteLine();
                new2serializer.Serialize(Console.Out, cc);
                Console.Read();
            }
        }
    }

    [XmlRoot("cats")]
    public class CatCollection
    {
        [XmlElement("cat")]
        public Cat[] Cats { get; set; }
    }

    [XmlRoot("cat")]
    public class Cat
    {
        //定义Color属性的序列化为cat节点的属性
        [XmlAttribute("color")]
        public string Color { get; set; }

        //要求不序列化Speed属性
        [XmlIgnore]
        public int Speed { get; set; }

        //设置Saying属性序列化为Xml子元素
        [XmlElement("saying")]
        public string Saying { get; set; }
    }
}
