/** Welcome banner: greeting, subtitle, inline-SVG mountain scene, motivational note. */
export function HeroSection({ greeting, name }: { greeting: string; name: string | null }) {
  return (
    <section className="hb-hero" aria-labelledby="hb-hero-title">
      <MountainScene />
      <div className="hb-hero-text">
        <h1 id="hb-hero-title">
          {greeting}
          {name ? `, ${name}` : ''}!{' '}
          <span aria-hidden="true">👋</span>
        </h1>
        <p>Here&apos;s what&apos;s happening across your projects today.</p>
      </div>
      <aside className="hb-quote" aria-label="Motivation">
        <p className="hb-quote-text">&ldquo;Small steps make big progress.&rdquo;</p>
        <p className="hb-quote-sub">— Keep going!</p>
      </aside>
    </section>
  )
}

function MountainScene() {
  return (
    <svg className="hb-mountains" viewBox="0 0 640 200" preserveAspectRatio="xMidYMax slice" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id="hb-sky" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#dbe9ff" />
          <stop offset="1" stopColor="#f3f7ff" stopOpacity="0" />
        </linearGradient>
        <linearGradient id="hb-far" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#c7d8f5" />
          <stop offset="1" stopColor="#a9bfe6" />
        </linearGradient>
        <linearGradient id="hb-near" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#9db7e8" />
          <stop offset="1" stopColor="#6f90cf" />
        </linearGradient>
        <linearGradient id="hb-snow" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#ffffff" />
          <stop offset="1" stopColor="#e6eefb" />
        </linearGradient>
      </defs>
      <rect width="640" height="200" fill="url(#hb-sky)" />
      <circle cx="330" cy="46" r="26" fill="#ffd28a" opacity="0.95" />
      <circle cx="330" cy="46" r="38" fill="#ffd28a" opacity="0.25" />
      <path d="M0 200 L120 110 L200 160 L290 70 L380 150 L470 90 L560 140 L640 60 L640 200 Z" fill="url(#hb-far)" />
      <path d="M290 70 L320 100 L340 92 L360 118 L330 110 L305 96 Z" fill="url(#hb-snow)" opacity="0.9" />
      <path d="M640 60 L610 92 L595 84 L570 112 L600 100 L625 88 Z" fill="url(#hb-snow)" opacity="0.9" />
      <path d="M0 200 L70 150 L150 190 L250 120 L330 178 L420 130 L510 176 L580 140 L640 190 L640 200 Z" fill="url(#hb-near)" />
      <path d="M250 120 L270 142 L285 136 L300 156 L280 148 L262 140 Z" fill="url(#hb-snow)" opacity="0.85" />
      <g fill="#4f7fc9" opacity="0.9">
        <path d="M120 200 L130 176 L140 200 Z" />
        <path d="M132 200 L140 170 L148 200 Z" />
        <path d="M480 200 L490 178 L500 200 Z" />
      </g>
    </svg>
  )
}
