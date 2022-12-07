package org.easydarwin.easyscreenlive.ui;

import android.databinding.DataBindingUtil;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.URLSpan;

import org.easydarwin.easyscreenlive.R;
import org.easydarwin.easyscreenlive.databinding.ActivityAboutBinding;


public class AboutActivity extends AppCompatActivity {

    private ActivityAboutBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        DataBindingUtil.setContentView(this, R.layout.activity_about);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_about);
        setSupportActionBar(binding.toolbar);
        {
            binding.title.setText("EasyPlayer RTSP-плеер：");
            binding.desc.setText("EasyScreenLive — это простая, эффективная и стабильная коллекция, кодирование," +
                    "Библиотека общего назначения, объединяющая службы потоковой передачи и потоковой передачи RTSP, отличающаяся низкой задержкой, высокой производительностью и низкой потерей пакетов." +
                    "В настоящее время поддерживает платформы Windows, Android, с помощью EasyScreenLive мы можем избежать доступа к несколько сложной коллекции аудио- и видеоисточников," +
                    "Кодирование и потоковая передача мультимедиа и процесс обслуживания RTSP / RTP / RTCP, нужно всего лишь вызвать несколько API-интерфейсов EasyScreenLive, вы можете легко," +
                    "Стабильная передача потоковых аудио- и видеоданных на сервер EasyDSS и публикация службы RTSP. Служба RTSP поддерживает два режима многоадресной и одноадресной рассылки. адрес проекта:");

            binding.desc.setMovementMethod(LinkMovementMethod.getInstance());
            SpannableString spannableString = new SpannableString("https://github.com/EasyDSS/easyscreenlive");
            //设置下划线文字
            spannableString.setSpan(new URLSpan("https://github.com/EasyDSS/easyscreenlive"), 0, spannableString.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

            //设置文字的前景色
            spannableString.setSpan(new ForegroundColorSpan(Color.RED), 0, spannableString.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
            binding.desc.append(spannableString);

        }
    }
}
