using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using OpenQA.Selenium;
using OpenQA.Selenium.Chrome;
using OpenQA.Selenium.IE;
using OpenQA.Selenium.Remote;
using OpenQA.Selenium.Support;
using System.Threading;

namespace GetDLLVersionFromUrl
{
    class Locator
    {
        public String loginurl { get; set; }
        public String loginName { get; set; }
        public String loginPassword { get; set; }

        public void Login()
        {
            var options = new ChromeOptions();
            DesiredCapabilities capabilities = DesiredCapabilities.Chrome();
            capabilities.SetCapability("chrome.switches", (object)("--start-maxisized"));
            options.AddArguments("--test-type", "--start-maximized");
            options.AddArguments("--test-type", "--ignore-certificate-errors");
            options.BinaryLocation = "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe";
            var driver = new ChromeDriver("C:\\Program Files (x86)\\Google\\Chrome\\Application", options);

            //var ieoptions = new InternetExplorerOptions();
            //DesiredCapabilities iecapabilities = DesiredCapabilities.InternetExplorer();
            //iecapabilities.SetCapability("internetexplorer.switches", (object)("--start-maxisized"));
            //ieoptions.AddAdditionalCapability("--test-type", "--start-maximized");
            //ieoptions.AddAdditionalCapability("--test-type", "--ignore-certificate-errors");
            //var iedriver = new InternetExplorerDriver(@"C:\Program Files (x86)\Internet Explorer");

            driver.Navigate().GoToUrl(loginurl);

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
    }
}
