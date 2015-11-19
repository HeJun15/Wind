using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Net;
using System.IO;
using System.Windows.Forms;
using OpenQA.Selenium;
using OpenQA.Selenium.Chrome;
using OpenQA.Selenium.Remote;
using System.Threading;

namespace GetDLLVersionFromUrl
{
    public class UrlLabel
    {
        //public String loginUrl { get; set; }
        public String loginName { get; set; }
        public String loginPassword { get; set; }
        public String dllUrl { get; set; }

        public static ChromeDriver driver = new ChromeDriver("C:\\Program Files (x86)\\Google\\Chrome\\Application");

        public UrlLabel(String loginname, String loginpassword, String dllurl)
        {
            //loginUrl = loginurl;
            loginName = loginname;
            loginPassword = loginpassword;
            dllUrl = dllurl;
        }

        public void Login(String loginUrl)
        {
            var options = new ChromeOptions();
            options.AddArguments("--test-type", "--start-maximized");
            options.AddArguments("--test-type", "--ignore-certificate-errors");
            options.BinaryLocation = "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe";
            driver = new ChromeDriver("C:\\Program Files (x86)\\Google\\Chrome\\Application", options);

            driver.Navigate().GoToUrl(loginUrl);

            int timeout = 0;
            while (driver.FindElements(By.ClassName("logbox")).Count == 0 && timeout < 500)
            {
                Thread.Sleep(1);
                timeout++;

            }

            IWebElement element = driver.FindElement(By.ClassName("logbox"));

            IWebElement ElName = element.FindElement(By.Name("username"));
            ElName.Clear();
            ElName.SendKeys(loginName);
            IWebElement ElPassword = element.FindElement(By.Id("password"));
            ElPassword.Clear();
            ElPassword.SendKeys(loginPassword);
            IWebElement ElLogin = element.FindElement(By.Id("IBtnLogin"));
            ElLogin.Click();
        }

        public String GetContent()
        {
            string result = null;
            WebResponse response = null;
            StreamReader reader = null;

            try
            {
                driver.Navigate().GoToUrl(dllUrl);
                String loginUrl = driver.Url;
                Login(loginUrl);

                int timeout = 0;
                while (driver.FindElements(By.ClassName("logbox")).Count == 0 && timeout < 500)
                {
                    Thread.Sleep(1);
                    timeout++;

                }

                //HttpWebRequest request = (HttpWebRequest)WebRequest.Create(dllUrl);
                ////FileWebRequest request = (FileWebRequest)WebRequest.Create(dllUrl);
                //request.Method = "GET";
                //response = request.GetResponse();
                //reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8);
                //result = reader.ReadToEnd();

                result = driver.FindElement(By.XPath("//html/body")).Text;
                
                Console.WriteLine();

                //driver.Close();
                return result;
            }
            catch (Exception ex)
            {
                MessageBox.Show( ex.Message );
                return result;
            }
            finally
            {
                if (reader != null)
                    reader.Close();
                if (response != null)
                    response.Close();
            }
        }
    }
}
