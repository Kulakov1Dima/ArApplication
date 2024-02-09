<a name="readme-top"></a>

<h1 align="center">IAR security system
<img src="https://ssau.ru/i/logo/logo-white-ru.svg" height="45"/></h1>
<p align="center">Integrated Augmented Reality security system</p>
<h3 align="center">Система обеспечения комплексной безопасности с применением функции дополненной реальности.</h3>


<details>
  <summary>Содержание</summary>
  <ol>
    <li>
      <a href="#О проекте">О проекте</a>
      <ul>
        <li><p>Используемые инструменты</p></li>
      </ul>
    </li>
    <li>
      <a href="#Начало работы">Начало работы</a>
      <ul>
        <li><p>Системные требования</p></li>
        <li><p>Установка</p></li>
      </ul>
    </li>
    <li><a href="#Использование">Использование</a></li>
    <li><a href="#Разработка">Разработка</a>
      <ul>
        <li><p>Зависимости проекта</p></li>
        <li><p>Запуск приложения</p></li>
      </ul>
    </li>
  </ol>
</details>

## О проекте
Проект направленный на создание системы оповещений и индикации средствами дополненной реальности. Итог проекта - программный продукт представляющий пользователю интерфейс взаимодействия с устройствами контроля доступа и видеонаблюдения. Система должна взаимодействовать с сдатчиками различных устройств, собирать данные о внешнем мире такие как видеоданные, производить индикацию и управление средствами дополненной реальности.

### Используемые инструменты
Функция дополненной реальности реализуется средствами SDK ARCore. ARCore — это SDK дополненной реальности Google, предлагающий кроссплатформенные API для создания новых иммерсивных приложений на Android, iOS, Unity и в Интернете.  IAR security system разработывается в Android Studio на языке Java.
<br></br>
<table style="border: none; border-spacing: 20px;">
  <tr>
    <td>
      <a href="https://developers.google.com/ar?hl=ru">
        <img src="https://developers.google.com/ar/images/logo.svg" height="55">
        <span style="vertical-align: middle;">ARCore</span>
      </a>
    </td>
    <td>
      <a href="https://developer.android.com/studio">
        <img src="https://developer.android.com/static/studio/images/android-studio-stable.svg" height="55">
        <span style="vertical-align: middle;">Android Studio</span>
      </a>
    </td>
  </tr>
</table>

<p align="right">(<a href="#readme-top">наверх</a>)</p>

## Начало работы

### Системные требования

### Установка

<p align="right">(<a href="#readme-top">наверх</a>)</p>

## Использование

<p align="right">(<a href="#readme-top">наверх</a>)</p>

## Разработка
Тут представлено описание кода программного продукта.

### Зависимости проекта
Проект использует библиотеки OpenGL ES и дополненной реальности:
```
implementation 'de.javagl:obj:0.4.0'
```
```
implementation 'com.google.ar:core:1.41.0'
```

### Запуск приложения
При запуске приложение проверяет возможность своей работы на устройстве. На устройстве должны быть установлены сервисы AR от google https://play.google.com/store/apps/details?id=com.google.ar.core
```Java
private void checkArcoreService(){
        ArCoreApk.getInstance().checkAvailabilityAsync(this, availability -> {
            if(!availability.isSupported()) {
               errorSupportInfo(); // извещение пользователя об ошибке
            }
            else{
                setContentView(R.layout.activity_main);
                Intent myIntent = new Intent(this, ARFrame.class);
                startActivity(myIntent);
            }
        });
    }
```
Если на устройстве не установлены сервисы AR или оно не поддерживается вы получите следующее сообщение:

![image](https://github.com/Kulakov1Dima/IAR-security-system/assets/84613812/10fea697-925a-47c2-8a0c-117f107f8f90)

<p align="right">(<a href="#readme-top">наверх</a>)</p>
