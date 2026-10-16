export default function SectionHeading({ icon, eyebrow, children, description }) {
  return (
    <div className="section-heading">
      <div className="eyebrow"><span aria-hidden="true">{icon}</span>{eyebrow}</div>
      <h2>{children}</h2>
      <p>{description}</p>
    </div>
  );
}
