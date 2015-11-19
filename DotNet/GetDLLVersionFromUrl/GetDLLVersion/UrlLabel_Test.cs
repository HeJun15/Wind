using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.IO;
using Newtonsoft.Json;

namespace GetDLLVersionFromUrl
{
    class ContentFromUrl_Test
    {
        static void Main(string[] args)
        {
            //String loginUrl = "https://cas.ctripcorp.com/caso/login?service=http%3A%2F%2Fcms.ops.ctripcorp.com%2Fj_spring_cas_security_check";
            String loginName = "hejun";
            String loginPassord = "648173654@qq";
            String dllUrl = "http://productws.ttd.uat.qa.nt.ctripcorp.com/ttd-box-api/appinternals/components/arch-cframework-startup-startupinfo?format=json";
            //String dll = "file:///D:/Users/hejun/Desktop/arch-cframework-startup-startupinfo.htm";

            //Locator loginLoc = new Locator();
            //loginLoc.loginName = "hejun";
            //loginLoc.loginPassword = "648173654@qq";
            //loginLoc.url = loginUrl;
            //loginLoc.Login();

            String content = null;
            UrlLabel urlLabel = new UrlLabel(loginName, loginPassord, dllUrl);
            content = urlLabel.GetContent();

            //string strSerializeJSON = JsonConvert.SerializeObject(person);
            //SerializeObject deserializeJson = (SerializeObject)JsonConvert.DeserializeObject(content, typeof(SerializeObject));

            //Console.WriteLine(deserializeJson.StartUpTime);
            //deserializeJson.GetDll();

            using (StreamWriter sw = new StreamWriter(@"d:\Users\hejun\Desktop\Output.html"))
            {
                sw.Write(content);
            }

            //AutoLogin autoLabel = new AutoLogin();
            //autoLabel.loginName = "hejun";
            //autoLabel.loginPassword = "648173654@qq";
            //autoLabel.loginUrl = testUrl;
            //content = autoLabel.Login();

            //Locator loginLoc = new Locator();
            //loginLoc.loginName = "hejun";
            //loginLoc.loginPassword = "648173654@qq";
            //loginLoc.url = loginUrl;
            //loginLoc.Login();
        }
    }
}
