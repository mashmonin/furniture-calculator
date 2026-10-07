import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { Alert, Badge, Menu, Space, Typography } from 'antd'
import { HomeOutlined, ShoppingCartOutlined } from '@ant-design/icons'
import { fetchUpdateCheck } from './api/updateCheck'
import { fetchDoorConfigurations, fetchHardwareCatalog } from './api/doorConfigurations'
import { CONFIGURATOR_SERVICES, DEFAULT_SERVICE, SERVICES, serviceByKey } from './services'
import type { UpdateCheckDto } from './api/types'
import ConfiguratorScreen, { type ConfiguratorLoadRequest } from './ConfiguratorScreen'
import CartScreen from './CartScreen'
import HardwareServiceScreen from './HardwareServiceScreen'
import { loadCart, saveCart, totalCartQuantity, type CartItem, type CartItemContent } from './cart'
import './App.css'

// Шапка приложения (см. specs/door-configurator-ui, «Шапка приложения»): название приложения — статичный
// текст; название файла прайс-листа приходит из каталога конфигураций (см. change add-price-list-source),
// на фронтенде остаётся только префикс подписи.
const APP_TITLE = 'Я-КОНФИГУРАТОР'
const PRICE_LIST_PREFIX = 'Прайс-лист: '
const HARDWARE_SERVICE_PRICE_LIST_CODE = 'PL-003'

// Длительность визуального эффекта «полёта» добавленной конфигурации к пункту «Корзина заказа» (см. flight
// ниже) — единственный источник правды для длительности: передаётся в CSS через инлайновый
// `animationDuration`, чтобы не дублировать то же число в App.css.
const CART_FLY_DURATION_MS = 650

// Два экрана приложения (см. change add-order-cart-screen) — конфигуратор и корзина заказа. Оба монтируются
// одновременно и переключаются видимостью (см. рендер ниже), а не условным рендером, чтобы переход на
// корзину и обратно не сбрасывал незавершённый выбор в ConfiguratorScreen (см. design.md, «Разбиение App.tsx
// на компоненты и переключение экрана»). Роутинг (react-router) не добавляется — экрана всего два, глубокие
// ссылки не нужны (см. proposal.md).
// Третий экран — сервис «Фурнитура» (см. change add-hardware-service), тоже монтируется всегда.
type AppScreen = 'configurator' | 'cart' | 'hardware'

function App() {
  const [screen, setScreen] = useState<AppScreen>('configurator')
  const [updateCheck, setUpdateCheck] = useState<UpdateCheckDto | null>(null)
  // Сервис, открытый на экране конфигуратора (см. services.ts). Конфигуратор каждого сервиса смонтирован
  // всегда (переключается видимость), поэтому незавершённый выбор не теряется при смене сервиса.
  const [activeServiceKey, setActiveServiceKey] = useState(DEFAULT_SERVICE.key)
  // Названия файлов прайс-листов по их коду — из общего (кэшированного) каталога конфигураций; пока каталог
  // не загружен или пуст, подпись прайс-листа в шапке не показывается.
  const [priceListNames, setPriceListNames] = useState<Record<string, string>>({})
  // Инициализация из localStorage один раз при монтировании (см. cart.ts, loadCart) — не эффектом, чтобы
  // не было промежуточного рендера с пустой корзиной перед первым чтением.
  const [cart, setCart] = useState<CartItem[]>(() => loadCart())
  const [cartSaveError, setCartSaveError] = useState(false)
  // Запрос на открытие позиции корзины обратно в конфигураторе (см. order-cart-ui, «Кнопка «Посмотреть»
  // открывает конфигурацию в конфигураторе») — requestedAt форсирует повторное срабатывание эффекта в
  // ConfiguratorScreen, даже если открывается та же самая позиция второй раз подряд.
  const [loadRequest, setLoadRequest] = useState<ConfiguratorLoadRequest | null>(null)
  // id позиции корзины, открытой через «Посмотреть» — пока задан, ConfiguratorScreen живьём синхронизирует
  // изменения в эту же позицию (см. order-cart-ui, «Живая синхронизация конфигурации, открытой из корзины»)
  // и блокирует кнопку «Добавить в корзину». Сбрасывается по «Очистить» (см. ConfiguratorScreen.onStopEditing).
  const [editingCartItemId, setEditingCartItemId] = useState<string | null>(null)
  // Растёт на 1 при каждом клике по пункту меню сервиса (см. правку пользователя) — сигнал для
  // ConfiguratorScreen этого сервиса принудительно очистить форму тем же способом, что и кнопка «Очистить»,
  // независимо от того, был ли уже открыт этот экран. Счётчик у каждого сервиса свой.
  const [configuratorResetKeys, setConfiguratorResetKeys] = useState<Record<string, number>>({})

  // Визуальный эффект «полёта» добавленной конфигурации к пункту «Корзина заказа» в левом меню (см. правку
  // пользователя) — cartMenuItemRef даёт координаты цели (пункт меню всегда в DOM, независимо от текущего
  // screen, см. рендер меню ниже), flight хранит координаты старта/финиша одного проигрывания анимации;
  // flightIdRef — счётчик, чтобы у каждого запуска был уникальный React key и CSS-анимация проигрывалась
  // заново, даже если добавить несколько конфигураций подряд быстрее длительности анимации.
  const cartMenuItemRef = useRef<HTMLSpanElement>(null)
  const flightIdRef = useRef(0)
  const [flight, setFlight] = useState<{ id: number; from: DOMRect; to: DOMRect } | null>(null)

  // Любое изменение корзины сразу пишется в localStorage (см. order-cart-ui, «Хранение корзины в
  // localStorage»); saveCart возвращает false при ошибке записи (хранилище недоступно/переполнено, см.
  // design.md, Risks) — это состояние читает ConfiguratorScreen через onAddToCart (см. группа 6), чтобы
  // показать сообщение об ошибке рядом с кнопкой «Добавить в корзину», не роняя уже показанный результат.
  function addCartItem(item: CartItem, sourceRect: DOMRect | null) {
    const next = [...cart, item]
    setCart(next)
    setCartSaveError(!saveCart(next))

    const targetRect = cartMenuItemRef.current?.getBoundingClientRect()
    if (sourceRect && targetRect) {
      flightIdRef.current += 1
      setFlight({ id: flightIdRef.current, from: sourceRect, to: targetRect })
    }
  }

  function updateCartItemQuantity(id: string, quantity: number) {
    const next = cart.map((item) => (item.id === id ? { ...item, quantity } : item))
    setCart(next)
    setCartSaveError(!saveCart(next))
  }

  function removeCartItem(id: string) {
    const next = cart.filter((item) => item.id !== id)
    setCart(next)
    setCartSaveError(!saveCart(next))
    // Если удаляемая позиция сейчас открыта в конфигураторе (editingCartItemId) — выходим из режима
    // редактирования: синхронизировать уже некуда, а кнопка «Добавить в корзину» должна снова заработать
    // (текущий, уже введённый выбор при этом не теряется — просто следующее «Добавить в корзину» создаст
    // новую позицию, как обычно).
    if (id === editingCartItemId) {
      setEditingCartItemId(null)
    }
  }

  function editCartItem(item: CartItem) {
    // Позиция открывается в своём сервисе (у старых позиций без serviceKey — «Эмаль и шпон»).
    const service = serviceByKey(item.serviceKey)
    setActiveServiceKey(service.key)
    setLoadRequest({ request: item.exportRequest, requestedAt: Date.now(), serviceKey: service.key })
    setEditingCartItemId(item.id)
    setScreen('configurator')
  }

  // Живая синхронизация (см. editingCartItemId выше) — заменяет содержимое позиции (всё, кроме id/addedAt/
  // quantity — их синхронизация не трогает, см. cart.ts, CartItemContent), пока пользователь меняет
  // конфигурацию в ConfiguratorScreen, не дожидаясь повторного «Добавить в корзину».
  function syncEditedCartItem(id: string, content: CartItemContent) {
    const next = cart.map((item) => (item.id === id ? { ...item, ...content } : item))
    setCart(next)
    setCartSaveError(!saveCart(next))
  }

  // Частичная синхронизация — только attributeTags, без ожидания валидного content (см. change
  // sync-cart-tags-immediately-on-cascade-reset): переключатели «Реверс»/«Четверть»/«Тип полотна» сбрасывают
  // каскад, и syncEditedCartItem выше в этот момент ничего не вызывает, пока модель не выбрана заново.
  function syncEditedCartItemTags(id: string, tags: string[]) {
    const next = cart.map((item) => (item.id === id ? { ...item, attributeTags: tags } : item))
    setCart(next)
    setCartSaveError(!saveCart(next))
  }

  // Замена позиции корзины целиком — сервис «Фурнитура» меняет у двери фурнитуру, цену и детализацию (см.
  // cart.ts, withHardware). Если эта позиция сейчас открыта в конфигураторе, режим редактирования завершается:
  // иначе живая синхронизация перезаписала бы позицию устаревшим списком фурнитуры конфигуратора.
  function replaceCartItem(updated: CartItem) {
    const next = cart.map((item) => (item.id === updated.id ? updated : item))
    setCart(next)
    setCartSaveError(!saveCart(next))
    if (updated.id === editingCartItemId) {
      setEditingCartItemId(null)
    }
  }

  function stopEditingCartItem() {
    setEditingCartItemId(null)
  }

  useEffect(() => {
    let cancelled = false
    fetchDoorConfigurations()
      .then((configurations) => {
        if (cancelled) {
          return
        }
        const names: Record<string, string> = {}
        for (const configuration of configurations) {
          const priceList = configuration.leaf.priceList
          if (priceList) {
            names[priceList.code] = priceList.name
          }
        }
        setPriceListNames(names)
      })
      .catch(() => {
        // Намеренно молча: ошибку каталога покажет сам конфигуратор, подпись прайс-листа просто не выводится.
      })
    return () => {
      cancelled = true
    }
  }, [])

  // Название файла прайс-листа сервиса «Фурнитура» (PL-003) берётся из каталога фурнитуры (в каталоге
  // конфигураций у него нет полотен) и показывается в шапке, как у остальных сервисов.
  useEffect(() => {
    let cancelled = false
    fetchHardwareCatalog()
      .then((catalog) => {
        if (cancelled) {
          return
        }
        for (const category of catalog) {
          for (const type of category.types) {
            if (type.priceList.code === HARDWARE_SERVICE_PRICE_LIST_CODE) {
              setPriceListNames((names) => ({ ...names, [type.priceList.code]: type.priceList.name }))
              return
            }
          }
        }
      })
      .catch(() => {
        // Намеренно молча: экран «Фурнитура» сам покажет ошибку каталога.
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    // Проверка обновлений не должна ничего блокировать — при ошибке просто не
    // показываем баннер (см. change add-desktop-app-packaging, раздел 5).
    fetchUpdateCheck()
      .then((data) => {
        if (!cancelled) {
          setUpdateCheck(data)
        }
      })
      .catch(() => {
        // Намеренно молча.
      })
    return () => {
      cancelled = true
    }
  }, [])

  const activeService = serviceByKey(activeServiceKey)
  const activePriceListName = priceListNames[activeService.priceListCode] ?? null
  // Режим редактирования позиции корзины действует только в конфигураторе её сервиса.
  const editingItem = cart.find((item) => item.id === editingCartItemId)
  const editingServiceKey = editingItem ? serviceByKey(editingItem.serviceKey).key : null

  return (
    <div className="page">
      <div className="app-header">
        <Space align="center" size={12}>
          <div className="app-header__logo">
            <HomeOutlined />
          </div>
          <Typography.Title level={5} style={{ margin: 0 }}>
            {APP_TITLE}
          </Typography.Title>
        </Space>
        <Space align="center" size={24}>
          {/* Подпись прайс-листа относится к каталогу конфигуратора — показывается только на экране «Эмаль
              и шпон»; счётчик «Корзина» — только на экране «Корзина заказа» (см. правку пользователя;
              переключение между экранами по-прежнему доступно через левое меню на обоих экранах). */}
          {screen === 'configurator' && activePriceListName && (
            <Typography.Text strong>{`${PRICE_LIST_PREFIX}${activePriceListName}`}</Typography.Text>
          )}
          {screen === 'hardware' && priceListNames[HARDWARE_SERVICE_PRICE_LIST_CODE] && (
            <Typography.Text strong>{`${PRICE_LIST_PREFIX}${priceListNames[HARDWARE_SERVICE_PRICE_LIST_CODE]}`}</Typography.Text>
          )}
          {screen === 'cart' && (
            <Badge count={totalCartQuantity(cart)} showZero size="small" offset={[6, 0]}>
              <Space size={4}>
                <ShoppingCartOutlined />
                Корзина
              </Space>
            </Badge>
          )}
        </Space>
      </div>

      {updateCheck?.updateAvailable && (
        <Alert
          style={{ marginBottom: 16 }}
          type="info"
          showIcon
          closable
          message={`Доступна новая версия приложения: ${updateCheck.latestVersion}`}
          description={
            updateCheck.downloadUrl && (
              <a href={updateCheck.downloadUrl} target="_blank" rel="noreferrer">
                Скачать обновление
              </a>
            )
          }
        />
      )}

      <div className="app-columns">
        <div className="app-sidebar">
          <Typography.Title level={5} style={{ marginTop: 0 }}>
            Сервисы
          </Typography.Title>
          <Menu
            mode="inline"
            selectable={false}
            selectedKeys={[screen === 'cart' ? 'cart' : screen === 'hardware' ? 'furnitura' : activeService.key]}
            items={[
              ...SERVICES.map((service) => ({ key: service.key, label: service.label })),
              // Обёрнуто в span с ref — не влияет на вид пункта меню, только даёт координаты цели для
              // эффекта «полёта» (см. addCartItem, flight выше); пункт всегда в DOM независимо от screen.
              { key: 'cart', label: <span ref={cartMenuItemRef}>Корзина</span> },
            ]}
            onClick={(info) => {
              if (info.key === 'cart') {
                setScreen('cart')
                return
              }
              // Сервис «Фурнитура» — отдельный экран без конфигуратора (см. services.ts, kind).
              if (serviceByKey(info.key).kind === 'hardware') {
                setScreen('hardware')
                return
              }
              // Клик по пункту сервиса принудительно очищает конфигуратор этого сервиса — тем же сбросом, что и
              // кнопка «Очистить» (см. правку пользователя) — независимо от того, был ли уже открыт этот экран.
              setActiveServiceKey(info.key)
              setConfiguratorResetKeys((keys) => ({ ...keys, [info.key]: (keys[info.key] ?? 0) + 1 }))
              setScreen('configurator')
            }}
          />
        </div>

        {CONFIGURATOR_SERVICES.map((service) => (
          <div
            key={service.key}
            style={{ display: screen === 'configurator' && activeService.key === service.key ? 'contents' : 'none' }}
          >
            <ConfiguratorScreen
              priceListCode={service.priceListCode}
              defaultThicknessMm={service.defaultThicknessMm}
              fixedPanelType={service.fixedPanelType}
              allowDoubleSided={service.allowDoubleSided}
              onAddToCart={(item, sourceRect) => addCartItem({ ...item, serviceKey: service.key }, sourceRect)}
              cartSaveError={cartSaveError}
              loadRequest={loadRequest?.serviceKey === service.key ? loadRequest : null}
              resetSignal={configuratorResetKeys[service.key] ?? 0}
              editingItemId={editingServiceKey === service.key ? editingCartItemId : null}
              onSyncEditedItem={syncEditedCartItem}
              onSyncEditedItemTags={syncEditedCartItemTags}
              onStopEditing={stopEditingCartItem}
              onReturnToCart={() => setScreen('cart')}
            />
          </div>
        ))}
        <div style={{ display: screen === 'hardware' ? 'contents' : 'none' }}>
          <HardwareServiceScreen
            items={cart}
            onUpdateItem={replaceCartItem}
            onGoToConfigurator={() => setScreen('configurator')}
          />
        </div>
        <div style={{ display: screen === 'cart' ? 'contents' : 'none' }}>
          <CartScreen
            items={cart}
            onUpdateQuantity={updateCartItemQuantity}
            onRemove={removeCartItem}
            onEdit={editCartItem}
            onGoToConfigurator={() => setScreen('configurator')}
          />
        </div>
      </div>

      {/* Летящая «частица» — визуальный эффект добавления в корзину (см. правку пользователя): фиксированно
          спозиционированный кружок, стартующий из координат кнопки «Добавить в корзину» и через CSS-анимацию
          (.cart-fly-particle, App.css) улетающий к пункту «Корзина заказа» в левом меню, угасая по пути.
          key={flight.id} гарантирует, что анимация проигрывается заново при каждом добавлении, даже если
          предыдущая ещё не завершилась. */}
      {flight && (
        <div
          key={flight.id}
          className="cart-fly-particle"
          style={
            {
              '--cart-fly-from-x': `${flight.from.left + flight.from.width / 2}px`,
              '--cart-fly-from-y': `${flight.from.top + flight.from.height / 2}px`,
              '--cart-fly-to-x': `${flight.to.left + flight.to.width / 2}px`,
              '--cart-fly-to-y': `${flight.to.top + flight.to.height / 2}px`,
              animationDuration: `${CART_FLY_DURATION_MS}ms`,
            } as CSSProperties
          }
          onAnimationEnd={() => setFlight(null)}
        />
      )}
    </div>
  )
}

export default App
