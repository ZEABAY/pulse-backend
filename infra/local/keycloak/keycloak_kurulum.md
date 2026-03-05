# Keycloak Manuel Kurulum ve Yapılandırma Rehberi

Bu doküman, `pulse` projesi için lokal Keycloak sunucusunun (`http://localhost:9080`) arayüz üzerinden (UI) nasıl yapılandırılacağını adım adım anlatmaktadır.

## 1. Keycloak Admin Arayüzüne Giriş
- Tarayıcınızdan **[http://localhost:9080](http://localhost:9080)** adresine gidin.
- **"Administration Console"** butonuna tıklayın.
- **Username:** `admin` ve **Password:** `admin` bilgileri ile giriş yapın.

> [!NOTE]
> Docker Compose konfigürasyonumuz `KC_BOOTSTRAP_ADMIN_USERNAME` değişkenleriyle kurulduğu için ilk girişiniz bu yetkili şifre ile olacaktır.

## 2. Pulse Realm Oluşturma
Keycloak varsayılan olarak "master" realm ile gelir. Uygulamamız kendi kullanıcılarını ayrı bir alanda tutmak için "pulse" isimli bir realm beklemektedir.

- Sol üst köşedeki **"master"** yazan açılır menüye tıklayın.
- Açılan listeden **"Create Realm"** butonuna basın.
- **Realm name:** `pulse` olarak girin.
- **"Create"** butonuna tıklayın.

## 3. Client Oluşturma (pulse-backend-client)
Spring Boot (*auth-service*) uygulamamızın Keycloak API'siyle konuşabilmesi ve kullanıcıların bu sisteme dahil olabilmesi için bir istemci (Client) tanımı gereklidir.

- Sol menüden **"Clients"** sekmesine tıklayın.
- Sağ üstteki **"Create client"** butonuna basın.

### Adım 3.1: General Settings (Genel Ayarlar)
- **Client type:** `OpenID Connect` (Varsayılan olarak kalsın).
- **Client ID:** `pulse-backend-client`
- *Diğer alanları boş bırakabilirsiniz.*
- **"Next"** butonuna tıklayın.

### Adım 3.2: Capability Config (Yetenek Ayarları)
Burada backend servisimizin bir kullanıcı gibi değil, bir "servis" olarak Keycloak ile konuşabilmesini sağlayacağız.
- **Client authentication:** Açık (ON) konuma getirin. (Bu "confidential" bir client olduğunu belirtir).
- **Authorization:** Kapalı kalabilir.
- **Authentication flow:**
  - `Standard flow`: **KAPATIN** (Tarayıcı yönlendirmesine ihtiyacımız yok).
  - `Direct access grants`: **AÇIK** kalsın (Kullanıcı adı şifre ile token almak için).
  - `Service accounts roles`: **AÇIK** kalsın (Spring Boot'un backend tarafında kullanıcı oluşturma yetkisi alabilmesi için).
- **"Next"** butonuna tıklayın.

### Adım 3.3: Login settings (Giriş Ayarları)
Bir backend REST API'si olduğumuz için buraları varsayılan (boş) bırakıp sağ alttaki **"Save"** butonuna tıklayın.

## 4. Client Secret'ı Almak ve Doğrulamak
Client'ı başarıyla kaydettikten sonra sistem bizi client detaylarına yönlendirecek.
- Client detay sayfasındaki sekmelerden **"Credentials"** sekmesine tıklayın.
- **"Client Secret"** hücresindeki değeri kopyalayın.
- Bu değerin **`my-secret`** olduğuna emin olun. Eğer farklı karmaşık bir şifre ise, `application.yml`'daki `keycloak.credentials.secret` değeri ile eşleşmeyecektir. 
- *Kolaylık olması için:* Sağdaki "Regenerate" butonunun yanındaki oka basarak (veya doğrudan Secret kutusuna girerek), bu secret değerini silip yerine **`my-secret`** yazabilirsiniz. Eğer Keycloak sürümü manuel değiştirmeye izin vermiyorsa, kopyaladığınız mevcut karmaşık şifreyi `auth-service/src/main/resources/application.yml` içine yapıştırmanız gerekmektedir.

## 5. Servis Hesabına Yetki Tanımlamak (Mecburi Adım)
`auth-service`, `pulse` realm'i içerisinde kullanıcı tanımlayacağı için yetkilendirilmesi gerekir.

- Yine `pulse-backend-client` sayfasındayken üst sekmelerden **"Service account roles"** sekmesine tıklayın.
- Sağ taraftaki **"Assign role"** butonuna tıklayın.
- Açılan pencerenin filtre kısmında **"Filter by clients"** seçeneğini seçin. (Aksi takdirde realm rolleri listelenir).
- Arama kutusuna `realm-management` yazıp Enter'a basın.
- Çıkan sonuçlar içerisinden:
  - `manage-users` (Kullanıcı oluşturma, silme, güncelleme yetkisi)
  - `view-users` (Kullanıcıları görüntüleme yetkisi)
Rollerini işaretleyin.
- **"Assign"** butonuna basın.

## Kurulum Tamamlandı
Tüm bu işlemleri gerçekleştirdiğinizde:
- `pulse` isimli bir realm'iniz oldu.
- `pulse-backend-client` id'li, yetkili gizli anahtarı (`my-secret`) olan bir istemciniz oldu.
- Bu istemci, "kullanıcı kayıt etme ve okuma" (`manage-users`, `view-users`) özelliklerine sahip oldu.

Artık `auth-service` uygulamasını başlatıp Postman üzerinden `/api/v1/auth/register` ucunu sorunsuzca test edebilirsiniz.
